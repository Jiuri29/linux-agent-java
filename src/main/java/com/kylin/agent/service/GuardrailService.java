package com.kylin.agent.service;

import com.kylin.agent.audit.AuditService;
import com.kylin.agent.domain.GuardrailExemption;
import com.kylin.agent.domain.GuardrailRule;
import com.kylin.agent.repository.GuardrailRepository;
import com.kylin.agent.security.AuthInterceptor;
import com.kylin.agent.security.Tokens;
import com.kylin.agent.web.AppException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Service
public class GuardrailService {

    private static final String DEFAULT_DENY = "^\\s*(mkfs\\b|dd\\b[^;]{0,120}of=/dev/(sd|vd|nvme|hd)|shutdown\\b|reboot\\b|init\\s+[06]\\b|:\\(\\)\\{)";
    private static final String DEFAULT_RM = "^\\s*rm\\b";
    private static final String DEFAULT_SERVICE = "^\\s*systemctl\\s+(restart|stop)\\b";
    private static final String DEFAULT_LOG_CLEAN = "(journalctl\\s+--vacuum|truncate\\s+-s\\s*0\\b)";

    private final GuardrailRepository repository;
    private final Tokens tokens;
    private final AuditService audit;

    public GuardrailService(GuardrailRepository repository, Tokens tokens, AuditService audit) {
        this.repository = repository;
        this.tokens = tokens;
        this.audit = audit;
    }

    public synchronized List<GuardrailRule> rules() {
        GuardrailRepository.GuardrailDocument doc = repository.load();
        if (doc.rules.isEmpty()) {
            seedDefaults(doc);
        }
        return List.copyOf(doc.rules);
    }

    public synchronized GuardrailRule saveRule(Map<String, Object> body) {
        GuardrailRepository.GuardrailDocument doc = loadWithDefaults();
        String action = str(body.getOrDefault("action", GuardrailRule.ACTION_APPROVE));
        if (!List.of(GuardrailRule.ACTION_ALLOW, GuardrailRule.ACTION_APPROVE, GuardrailRule.ACTION_DENY)
                .contains(action)) {
            throw AppException.validation("action must be allow, approve or deny");
        }
        String commandPattern = str(body.get("command_pattern"));
        String intentPattern = str(body.get("intent_pattern"));
        validateRegex(commandPattern, "command_pattern");
        validateRegex(intentPattern, "intent_pattern");
        if ((commandPattern == null || commandPattern.isBlank())
                && (intentPattern == null || intentPattern.isBlank())) {
            throw AppException.validation("command_pattern or intent_pattern is required");
        }
        for (GuardrailRule existing : doc.rules) {
            if (existing.builtin && str(body.get("id")) != null
                    && existing.id.equals(str(body.get("id")))) {
                throw AppException.validation("builtin rule cannot be overwritten");
            }
        }

        GuardrailRule rule = new GuardrailRule();
        Object id = body.get("id");
        rule.id = id == null ? "gr-" + tokens.shortId() : String.valueOf(id);
        rule.name = requireName(str(body.get("name")));
        rule.intentPattern = intentPattern;
        rule.commandPattern = commandPattern;
        rule.action = action;
        rule.severity = str(body.getOrDefault("severity", "warning"));
        rule.enabled = true;
        rule.builtin = false;
        rule.description = str(body.get("description"));
        rule.createdAt = Instant.now();

        doc.rules.removeIf(r -> r.id.equals(rule.id));
        doc.rules.add(rule);
        repository.save(doc);
        audit.append("guardrail", operator(), "rule-save", "rule:" + rule.id, "success",
                Map.of("action", action));
        return rule;
    }

    public synchronized GuardrailRule setStatus(Map<String, Object> body) {
        GuardrailRepository.GuardrailDocument doc = loadWithDefaults();
        String id = requireId(str(body.get("id")));
        GuardrailRule rule = doc.rules.stream()
                .filter(r -> r.id.equals(id))
                .findFirst()
                .orElseThrow(() -> AppException.notFound("rule_not_found"));
        Object enabled = body.get("enabled");
        rule.enabled = enabled instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(enabled));
        repository.save(doc);
        audit.append("guardrail", operator(), rule.enabled ? "rule-enable" : "rule-disable",
                "rule:" + id, "success", Map.of());
        return rule;
    }

    public Map<String, Object> testRule(Map<String, Object> body) {
        String id = str(body.get("id"));
        GuardrailRule probe = new GuardrailRule();
        if (id != null && !id.isBlank()) {
            GuardrailRule existing = rules().stream()
                    .filter(r -> r.id.equals(id))
                    .findFirst()
                    .orElseThrow(() -> AppException.notFound("rule_not_found"));
            probe.commandPattern = existing.commandPattern;
            probe.intentPattern = existing.intentPattern;
            probe.action = existing.action;
            probe.name = existing.name;
        } else {
            probe.commandPattern = str(body.get("command_pattern"));
            probe.intentPattern = str(body.get("intent_pattern"));
            probe.action = str(body.getOrDefault("action", GuardrailRule.ACTION_APPROVE));
            probe.name = "ad-hoc";
        }
        String inputCommand = str(body.getOrDefault("command", ""));
        String inputIntent = str(body.getOrDefault("intent", ""));
        boolean matched = probe.matches(inputIntent, inputCommand);
        return Map.of("matched", matched, "action", matched ? probe.action : "none",
                "name", probe.name);
    }

    public synchronized List<GuardrailExemption> exemptions() {
        return List.copyOf(loadWithDefaults().exemptions);
    }

    public synchronized GuardrailExemption addExemption(Map<String, Object> body) {
        GuardrailRepository.GuardrailDocument doc = loadWithDefaults();
        String pattern = requireName(str(body.get("pattern")));
        validateRegex(pattern, "pattern");
        Long until = null;
        if (body.get("until_epoch_ms") instanceof Number n) {
            until = n.longValue();
        }
        GuardrailExemption exemption = new GuardrailExemption(
                "gex-" + tokens.shortId(), pattern, str(body.get("reason")),
                operator(), until, Instant.now());
        doc.exemptions.removeIf(e -> e.pattern().equals(pattern));
        doc.exemptions.add(exemption);
        repository.save(doc);
        audit.append("guardrail", operator(), "exemption-add", pattern, "success", Map.of());
        return exemption;
    }

    public synchronized Map<String, Object> evaluate(String intent, List<String> commands) {
        if (commands == null || commands.isEmpty()) {
            throw AppException.validation("commands is required");
        }
        GuardrailRepository.GuardrailDocument doc = loadWithDefaults();
        long now = System.currentTimeMillis();

        String overall = "allowed";
        List<GuardrailRepository.DecisionLine> decisions = new ArrayList<>();
        for (String command : commands) {
            GuardrailRepository.DecisionLine line = decide(doc, intent, command, now);
            decisions.add(line);
            overall = strictest(overall, line.decision.equals("approval_required")
                    ? "approval_required"
                    : line.decision.equals("blocked") ? "blocked" : "allowed");
        }

        if (!"allowed".equals(overall)) {
            GuardrailRepository.ApprovalRequest request = new GuardrailRepository.ApprovalRequest();
            request.id = "gap-" + tokens.shortId();
            request.ts = Instant.now().toString();
            request.operator = operator();
            request.intent = intent;
            request.overall = overall;
            request.decisions = decisions;
            repository.appendApproval(request);
        }
        audit.append("guardrail", operator(), "evaluate", truncate(intent, 120), overall,
                Map.of("commands", commands.size(), "overall", overall));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("overall", overall);
        result.put("decisions", decisions);
        result.put("evaluated_at", Instant.now().toString());
        return result;
    }

    public List<GuardrailRepository.ApprovalRequest> approvals(int limit) {
        return repository.readApprovals(Math.min(Math.max(limit, 1), 500));
    }

    private GuardrailRepository.DecisionLine decide(GuardrailRepository.GuardrailDocument doc,
                                                    String intent, String command, long now) {
        GuardrailRepository.DecisionLine line = new GuardrailRepository.DecisionLine();
        line.command = command;

        for (GuardrailExemption exemption : doc.exemptions) {
            try {
                if (exemption.active(now)
                        && Pattern.compile(exemption.pattern()).matcher(command).find()) {
                    line.decision = "allowed";
                    line.exempt_by = exemption.id();
                    return line;
                }
            } catch (PatternSyntaxException ignored) {
                // skip invalid exemption pattern
            }
        }

        String decision = null;
        String severity = null;
        String matchedRule = null;
        for (GuardrailRule rule : doc.rules) {
            if (!rule.enabled || !rule.appliesToIntent(intent)) {
                continue;
            }
            if (rule.matches(intent, command)) {
                if ("deny".equals(rule.action)) {
                    decision = "blocked";
                    severity = rule.severity;
                    matchedRule = rule.id;
                    break;
                } else if ("approve".equals(rule.action) && !"blocked".equals(decision)) {
                    decision = "approval_required";
                    severity = rule.severity;
                    matchedRule = rule.id;
                } else if ("allow".equals(rule.action) && decision == null) {
                    decision = "allowed";
                    severity = rule.severity;
                    matchedRule = rule.id;
                }
            }
        }
        line.decision = decision == null ? "allowed" : decision;
        line.matched_rule = matchedRule;
        line.severity = severity;
        return line;
    }

    private static String strictest(String current, String candidate) {
        if ("blocked".equals(current) || "blocked".equals(candidate)) {
            return "blocked";
        }
        if ("approval_required".equals(current) || "approval_required".equals(candidate)) {
            return "approval_required";
        }
        return "allowed";
    }

    private void seedDefaults(GuardrailRepository.GuardrailDocument doc) {
        doc.rules.add(builtIn("gd-deny-catastrophic", "灾难性破坏命令拦截", DEFAULT_DENY,
                GuardrailRule.ACTION_DENY, "critical", "mkfs/dd 写盘/关机重启/fork 炸弹等直接阻断"));
        doc.rules.add(builtIn("gd-approve-rm", "删除操作需审批", DEFAULT_RM,
                GuardrailRule.ACTION_APPROVE, "warning", "所有 rm 命令需要审批确认"));
        doc.rules.add(builtIn("gd-approve-service-restart", "服务停止重启需审批", DEFAULT_SERVICE,
                GuardrailRule.ACTION_APPROVE, "major", "systemctl restart/stop 需审批"));
        doc.rules.add(builtIn("gd-approve-log-clean", "日志清理需审批", DEFAULT_LOG_CLEAN,
                GuardrailRule.ACTION_APPROVE, "warning", "日志清理动作需审批"));
        repository.save(doc);
    }

    private GuardrailRule builtIn(String id, String name, String pattern,
                                  String action, String severity, String description) {
        GuardrailRule rule = new GuardrailRule();
        rule.id = id;
        rule.name = name;
        rule.commandPattern = pattern;
        rule.action = action;
        rule.severity = severity;
        rule.enabled = true;
        rule.builtin = true;
        rule.description = description;
        rule.createdAt = Instant.now();
        return rule;
    }

    private GuardrailRepository.GuardrailDocument loadWithDefaults() {
        GuardrailRepository.GuardrailDocument doc = repository.load();
        if (doc.rules.isEmpty()) {
            seedDefaults(doc);
        }
        return doc;
    }

    private static void validateRegex(String pattern, String field) {
        if (pattern == null || pattern.isBlank()) {
            return;
        }
        try {
            Pattern.compile(pattern);
        } catch (PatternSyntaxException e) {
            throw AppException.validation(field + " is not a valid regex: " + e.getMessage());
        }
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw AppException.validation("name is required");
        }
        return name;
    }

    private static String requireId(String id) {
        if (id == null || id.isBlank()) {
            throw AppException.validation("id is required");
        }
        return id;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() > max ? value.substring(0, max) : value;
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
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
