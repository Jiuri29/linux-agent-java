package com.linux.agent.service;

import com.linux.agent.audit.AuditService;
import com.linux.agent.domain.TerminalTemplate;
import com.linux.agent.repository.TerminalRepository;
import com.linux.agent.security.AuthInterceptor;
import com.linux.agent.security.TerminalPolicy;
import com.linux.agent.security.Tokens;
import com.linux.agent.web.AppException;
import com.linux.agent.web.ErrorCode;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TerminalService {

    private static final int TIMEOUT_SECONDS = 20;
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.-]+)\\s*}}");
    private static final Pattern RUN_ID = Pattern.compile("^run-[0-9a-fA-F]{8}$");

    private final TerminalRepository repository;
    private final TerminalPolicy policy;
    private final Tokens tokens;
    private final AuditService audit;

    public TerminalService(TerminalRepository repository, TerminalPolicy policy,
                           Tokens tokens, AuditService audit) {
        this.repository = repository;
        this.policy = policy;
        this.tokens = tokens;
        this.audit = audit;
    }

    public List<TerminalTemplate> templates() {
        return List.copyOf(repository.loadTemplates());
    }

    public synchronized TerminalTemplate saveTemplate(Map<String, Object> body) {
        String name = str(body.get("name"));
        String commandTemplate = str(body.get("command_template"));
        if (name == null || name.isBlank()) {
            throw AppException.validation("name is required");
        }
        if (commandTemplate == null || commandTemplate.isBlank()) {
            throw AppException.validation("command_template is required");
        }
        TerminalTemplate template = new TerminalTemplate();
        Object id = body.get("id");
        template.id = id == null ? "tpl-" + tokens.shortId() : String.valueOf(id);
        template.name = name;
        template.description = str(body.get("description"));
        template.commandTemplate = commandTemplate;
        template.createdAt = Instant.now();

        List<TerminalTemplate> all = new ArrayList<>(repository.loadTemplates().stream()
                .filter(t -> !t.id.equals(template.id))
                .toList());
        all.add(template);
        repository.saveTemplates(all);
        audit.append("terminal", operator(), "template-save", "template:" + template.id, "success", Map.of());
        return template;
    }

    public Map<String, Object> renderTemplate(String templateId, Map<String, Object> params) {
        TerminalTemplate template = repository.loadTemplates().stream()
                .filter(t -> t.id.equals(templateId))
                .findFirst()
                .orElseThrow(() -> AppException.notFound("template_not_found"));
        String rendered = render(template.commandTemplate, params == null ? Map.of() : params);
        return Map.of("id", template.id, "name", template.name, "command", rendered);
    }

    public synchronized Map<String, Object> run(Map<String, Object> body) {
        String command = resolveCommand(body);
        String operator = operator();
        String id = "run-" + tokens.shortId();

        TerminalPolicy.Verdict verdict = policy.evaluate(command);
        if (!verdict.allowed()) {
            recordHistory(id, operator, command, -1, 0, false, verdict.reason(), "");
            audit.append("terminal", operator, "run-blocked", truncate(command, 200), "blocked",
                    Map.of("reason", String.valueOf(verdict.reason())));
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "command not allowed: " + verdict.reason(),
                    Map.of("error", "command_not_allowed", "reason", String.valueOf(verdict.reason())));
        }

        long start = System.currentTimeMillis();
        Execution execution = execute(tokenize(command));
        long duration = System.currentTimeMillis() - start;

        recordHistory(id, operator, command, execution.exitCode(), duration, true, null,
                ("stdout:\n" + execution.stdout() + "\nstderr:\n" + execution.stderr()).trim());

        audit.append("terminal", operator, "run", truncate(command, 200),
                execution.exitCode() == 0 ? "success" : "failure",
                Map.of("exit_code", execution.exitCode(), "duration_ms", duration));
        return Map.of(
                "id", id,
                "exit_code", execution.exitCode(),
                "stdout", execution.stdout(),
                "stderr", execution.stderr(),
                "duration_ms", duration);
    }

    public List<TerminalRepository.HistoryEntry> history(int limit) {
        return repository.readHistory(Math.min(Math.max(limit, 1), 500));
    }

    public Map<String, Object> replay(String id) {
        String safeId = requireNonBlank(id);
        if (!RUN_ID.matcher(safeId).matches()) {
            throw AppException.notFound("run_not_found");
        }
        String output = repository.readOutput(safeId);
        if (output == null) {
            throw AppException.notFound("run_not_found");
        }
        return Map.of("id", safeId, "output", output);
    }

    private String resolveCommand(Map<String, Object> body) {
        Object direct = body.get("command");
        if (direct != null && !String.valueOf(direct).isBlank()) {
            return String.valueOf(direct).trim();
        }
        Object templateId = body.get("template_id");
        if (templateId == null) {
            throw AppException.validation("command or template_id is required");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> params = body.containsKey("params") && body.get("params") instanceof Map
                ? (Map<String, Object>) body.get("params")
                : Map.of();
        return (String) renderTemplate(String.valueOf(templateId), params).get("command");
    }

    private String render(String commandTemplate, Map<String, Object> params) {
        Matcher matcher = PLACEHOLDER.matcher(commandTemplate);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = params.get(key);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value == null ? "" : String.valueOf(value)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private void recordHistory(String id, String operator, String command, int exitCode, long durationMs,
                               boolean allowed, String reason, String output) {
        TerminalRepository.HistoryEntry entry = new TerminalRepository.HistoryEntry();
        entry.id = id;
        entry.ts = Instant.now().toString();
        entry.operator = operator;
        entry.command = command;
        entry.exit_code = exitCode;
        entry.duration_ms = durationMs;
        entry.allowed = allowed;
        entry.reason = reason;
        repository.appendHistory(entry);
        repository.writeOutput(id, output == null ? "" : output);
    }

    private Execution execute(String[] tokens) {
        com.linux.agent.util.ProcessExec.Result result = com.linux.agent.util.ProcessExec.run(
                new ProcessBuilder(tokens), TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (result.timedOut()) {
            return new Execution(-1, "", "timeout after " + TIMEOUT_SECONDS + "s");
        }
        if (result.failure() != null) {
            return new Execution(-1, "", "exec failed: " + result.failure().getMessage());
        }
        return new Execution(result.exitCode(), result.stdout(), result.stderr());
    }

    static String[] tokenize(String command) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingle = false;
        boolean inDouble = false;
        boolean hasToken = false;
        char[] chars = command.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (inSingle) {
                if (c == '\'') {
                    inSingle = false;
                } else {
                    current.append(c);
                }
            } else if (inDouble) {
                if (c == '"') {
                    inDouble = false;
                } else if (c == '\\' && i + 1 < chars.length) {
                    current.append(chars[++i]);
                } else {
                    current.append(c);
                }
            } else if (c == '\'') {
                inSingle = true;
                hasToken = true;
            } else if (c == '"') {
                inDouble = true;
                hasToken = true;
            } else if (c == '\\' && i + 1 < chars.length) {
                current.append(chars[++i]);
                hasToken = true;
            } else if (Character.isWhitespace(c)) {
                if (hasToken) {
                    out.add(current.toString());
                    current.setLength(0);
                    hasToken = false;
                }
            } else {
                current.append(c);
                hasToken = true;
            }
        }
        if (hasToken) {
            out.add(current.toString());
        }
        return out.toArray(new String[0]);
    }

    private static String requireNonBlank(String value) {
        if (value == null || value.isBlank()) {
            throw AppException.validation("id is required");
        }
        return value;
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String truncate(String value, int max) {
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

    private record Execution(int exitCode, String stdout, String stderr) {
    }
}
