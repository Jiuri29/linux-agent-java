package com.kylin.agent.service;

import com.kylin.agent.audit.AuditService;
import com.kylin.agent.repository.ChatopsRepository;
import com.kylin.agent.repository.GuardrailRepository;
import com.kylin.agent.security.AuthInterceptor;
import com.kylin.agent.security.Tokens;
import com.kylin.agent.web.AppException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ChatopsService {

    private static final Pattern COMMAND_LINE = Pattern.compile("(?m)^\\$\\s+(.+)$");

    public record Candidate(String command, String decision, String matchedRule) {
    }

    private final ChatopsRepository sessions;
    private final AgentConfigService agentConfig;
    private final GuardrailService guardrails;
    private final Tokens tokens;
    private final AuditService audit;

    public ChatopsService(ChatopsRepository sessions, AgentConfigService agentConfig,
                          GuardrailService guardrails, Tokens tokens, AuditService audit) {
        this.sessions = sessions;
        this.agentConfig = agentConfig;
        this.guardrails = guardrails;
        this.tokens = tokens;
        this.audit = audit;
    }

    public List<Map<String, Object>> templates() {
        List<Map<String, Object>> result = new ArrayList<>();
        result.add(Map.of("id", "inspect", "name", "主机巡检",
                "prompt", "请给出本机巡检建议，候选命令以 $ 开头的行返回"));
        result.add(Map.of("id", "disk-cleanup", "name", "磁盘清理",
                "prompt", "磁盘空间不足，请给出安全的清理命令（$ 开头）"));
        result.add(Map.of("id", "service-check", "name", "服务检查",
                "prompt", "请给出检查服务状态的只读命令（$ 开头）"));
        return result;
    }

    public synchronized Map<String, Object> sendMessage(String sessionId, String message) {
        if (message == null || message.isBlank()) {
            throw AppException.validation("message is required");
        }
        ChatopsRepository.ChatSession session = resolveSession(sessionId);
        session.messages.add(ChatopsRepository.message("user", message));

        String reply = agentConfig.chatCompletion(message);
        List<String> commands = extractCommands(reply);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("session_id", session.id);
        result.put("reply", reply);

        if (commands.isEmpty()) {
            session.messages.add(ChatopsRepository.message("assistant", reply));
            persist(session);
            result.put("candidates", List.of());
            result.put("requires_confirm", false);
            return result;
        }

        Map<String, Object> evaluation = guardrails.evaluate(
                "chatops: " + truncate(message, 100), commands);
        @SuppressWarnings("unchecked")
        List<GuardrailRepository.DecisionLine> decisions =
                (List<GuardrailRepository.DecisionLine>) (Object) evaluation.get("decisions");
        boolean requiresConfirm = !"allowed".equals(evaluation.get("overall"));

        List<Candidate> candidates = new ArrayList<>();
        for (int i = 0; i < commands.size(); i++) {
            GuardrailRepository.DecisionLine line = decisions.get(Math.min(i, decisions.size() - 1));
            candidates.add(new Candidate(commands.get(i), line.decision, line.matched_rule));
        }

        if (requiresConfirm) {
            String actionId = "act-" + tokens.shortId();
            Map<String, Object> pending = new LinkedHashMap<>();
            pending.put("action_id", actionId);
            pending.put("commands", commands);
            pending.put("overall", evaluation.get("overall"));
            pending.put("decisions", decisions);
            pending.put("confirmed", false);
            session.pendingActions.add(pending);
            session.messages.add(ChatopsRepository.message("assistant",
                    reply + "\n[护栏] 检测到需审批/禁止的候选命令，已挂起待确认。"));
            result.put("action_id", actionId);
        } else {
            session.messages.add(ChatopsRepository.message("assistant", reply));
        }

        persist(session);
        result.put("candidates", candidates);
        result.put("requires_confirm", requiresConfirm);
        audit.append("chatops", operator(), "message", truncate(message, 120),
                requiresConfirm ? "approval_required" : "success",
                Map.of("session_id", session.id));
        return result;
    }

    public synchronized Map<String, Object> confirm(String sessionId, String actionId) {
        ChatopsRepository.ChatSession session = findSession(sessionId);
        for (Map<String, Object> pending : session.pendingActions) {
            if (actionId.equals(String.valueOf(pending.get("action_id")))) {
                if (Boolean.parseBoolean(String.valueOf(pending.get("confirmed")))) {
                    throw AppException.validation("action already confirmed");
                }
                if ("blocked".equals(String.valueOf(pending.get("overall")))) {
                    throw AppException.validation("blocked actions cannot be confirmed");
                }
                pending.put("confirmed", true);
                pending.put("confirmed_at", java.time.Instant.now().toString());
                session.messages.add(ChatopsRepository.message("system",
                        "操作 " + actionId + " 已由运维确认（未自动执行）"));
                persist(session);
                audit.append("chatops", operator(), "confirm", actionId, "success",
                        Map.of("session_id", session.id));
                return Map.of("status", "ok", "action_id", actionId,
                        "note", "commands are NOT auto-executed; use /api/terminal/run manually");
            }
        }
        throw AppException.notFound("action_not_found");
    }

    public Map<String, Object> sessionDetail(String sessionId) {
        ChatopsRepository.ChatSession session = findSession(sessionId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", session.id);
        result.put("created_at", String.valueOf(session.createdAt));
        result.put("messages", session.messages);
        result.put("pending_actions", session.pendingActions);
        return result;
    }

    public Map<String, Object> demoCleanup() {
        List<String> commands = List.of(
                "journalctl --vacuum-time=7d",
                "rm -rf /var/tmp/demo-*",
                "df -kP");
        Map<String, Object> evaluation = guardrails.evaluate("演示清理流程（不执行写入动作）", commands);
        audit.append("chatops", operator(), "demo-cleanup", "workflow", "simulated",
                Map.of("overall", String.valueOf(evaluation.get("overall"))));
        return Map.of(
                "status", "ok",
                "note", "演示流程仅做护栏评估，不执行任何写入动作",
                "evaluation", evaluation);
    }

    private void persist(ChatopsRepository.ChatSession session) {
        List<ChatopsRepository.ChatSession> all = new ArrayList<>(
                sessions.loadAll().stream()
                        .filter(s -> !s.id.equals(session.id))
                        .toList());
        all.add(session);
        sessions.saveAll(all);
    }

    private ChatopsRepository.ChatSession resolveSession(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            return findSession(sessionId);
        }
        return sessions.create();
    }

    private ChatopsRepository.ChatSession findSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw AppException.validation("session_id is required");
        }
        return sessions.loadAll().stream()
                .filter(s -> s.id.equals(sessionId))
                .findFirst()
                .orElseThrow(() -> AppException.notFound("session_not_found"));
    }

    private static List<String> extractCommands(String reply) {
        List<String> commands = new ArrayList<>();
        Matcher matcher = COMMAND_LINE.matcher(reply == null ? "" : reply);
        while (matcher.find()) {
            commands.add(matcher.group(1).trim());
        }
        return commands;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() > max ? value.substring(0, max) : value;
    }

    private String operator() {
        var attributes = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (attributes instanceof org.springframework.web.context.request.ServletRequestAttributes servlet) {
            Object operator = servlet.getRequest().getAttribute(AuthInterceptor.OPERATOR_ATTRIBUTE);
            if (operator != null) {
                return operator.toString();
            }
        }
        return "system";
    }
}
