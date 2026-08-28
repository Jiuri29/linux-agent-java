package com.linux.agent.security;

import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.List;
import java.util.Optional;

@Component
public class PermissionMapper {

    private record Rule(String method, String pattern, String permission) {
    }

    private final AntPathMatcher matcher = new AntPathMatcher();
    private final List<Rule> rules = List.of(
            new Rule("GET", "/api/users", "user:manage"),
            new Rule("GET", "/api/users/**", "user:manage"),
            new Rule("POST", "/api/users", "user:manage"),
            new Rule("POST", "/api/users/**", "user:manage"),
            new Rule("GET", "/api/roles", "role:read"),
            new Rule("POST", "/api/roles", "role:manage"),
            new Rule("POST", "/api/roles/**", "role:manage"),

            new Rule("POST", "/api/assets", "asset:edit"),
            new Rule("POST", "/api/assets/update", "asset:edit"),
            new Rule("POST", "/api/assets/groups", "asset:edit"),
            new Rule("POST", "/api/assets/tags", "asset:edit"),
            new Rule("POST", "/api/assets/applications", "asset:edit"),
            new Rule("POST", "/api/assets/services", "asset:edit"),
            new Rule("POST", "/api/assets/dependencies", "asset:edit"),
            new Rule("POST", "/api/assets/delete", "asset:delete"),
            new Rule("POST", "/api/assets/groups/delete", "asset:delete"),
            new Rule("POST", "/api/assets/tags/delete", "asset:delete"),
            new Rule("POST", "/api/assets/sync", "asset:sync"),
            new Rule("GET", "/api/assets/detail", "asset:read"),
            new Rule("GET", "/api/assets/groups", "asset:read"),
            new Rule("GET", "/api/assets/tags", "asset:read"),
            new Rule("GET", "/api/assets/applications", "asset:read"),
            new Rule("GET", "/api/assets/snapshots", "asset:read"),
            new Rule("GET", "/api/assets/**", "asset:read"),
            new Rule("GET", "/api/assets", "asset:read"),

            new Rule("GET", "/api/monitor/**", "monitor:read"),
            new Rule("POST", "/api/monitor/alert-rules", "monitor:manage"),
            new Rule("POST", "/api/monitor/rules", "monitor:manage"),
            new Rule("POST", "/api/monitor/simulate", "monitor:manage"),
            new Rule("POST", "/api/monitor/alerts/**", "monitor:manage"),
            new Rule("POST", "/api/monitor/suppressions", "monitor:manage"),
            new Rule("GET", "/api/os/**", "asset:read"),
            new Rule("GET", "/api/risk/scan", "risk:scan"),

            new Rule("POST", "/api/services/config/versions", "config:modify"),
            new Rule("POST", "/api/services/config/rollback", "config:rollback"),
            new Rule("POST", "/api/services/**", "service:operate"),
            new Rule("POST", "/api/services", "service:operate"),
            new Rule("*", "/api/services/**", "service:read"),
            new Rule("*", "/api/services", "service:read"),

            new Rule("POST", "/api/terminal/templates/render", "terminal:view"),
            new Rule("POST", "/api/terminal/templates", "terminal:template:manage"),
            new Rule("POST", "/api/terminal/run", "terminal:execute"),
            new Rule("*", "/api/terminal/templates", "terminal:view"),
            new Rule("GET", "/api/terminal/history", "terminal:view"),
            new Rule("GET", "/api/terminal/replay", "terminal:replay:view"),

            new Rule("GET", "/api/files/download", "file:download"),
            new Rule("GET", "/api/files/**", "file:read"),
            new Rule("GET", "/api/files", "file:read"),
            new Rule("POST", "/api/files/write", "file:write"),
            new Rule("POST", "/api/files/upload", "file:upload"),
            new Rule("POST", "/api/files/delete", "file:delete"),
            new Rule("POST", "/api/files/restore", "file:restore"),
            new Rule("POST", "/api/files/operation/**", "file:plan"),

            new Rule("*", "/api/guardrails/**", "guardrail:evaluate"),

            new Rule("GET", "/api/audit/export", "audit:export"),
            new Rule("POST", "/api/audit/archive", "audit:archive"),
            new Rule("GET", "/api/audit/integrity", "audit:verify"),
            new Rule("GET", "/api/audit/**", "audit:read"),

            new Rule("GET", "/api/admin/**", "admin:read"),
            new Rule("GET", "/api/runtime/**", "asset:read"),

            new Rule("*", "/api/tests/**", "test:run"),
            new Rule("*", "/api/tests", "test:run"),

            new Rule("POST", "/api/chatops/**", "chatops:use"),
            new Rule("POST", "/api/chatops", "chatops:use"),
            new Rule("*", "/api/chatops/**", "chatops:use"),

            new Rule("POST", "/api/mcp/plugins/register", "mcp:manage"),
            new Rule("POST", "/api/mcp/plugins/status", "mcp:manage"),
            new Rule("POST", "/api/mcp/plugins/uninstall", "mcp:manage"),
            new Rule("POST", "/api/mcp/tools/status", "mcp:manage"),
            new Rule("POST", "/api/mcp/invoke", "mcp:invoke"),
            new Rule("POST", "/api/mcp/rpc", "mcp:invoke"),
            new Rule("*", "/api/mcp/**", "mcp:invoke"),

            new Rule("GET", "/api/config/agent-api", "config:manage"),
            new Rule("GET", "/api/config/agent-api/test", "config:manage"),
            new Rule("POST", "/api/config/agent-api/test", "config:manage"),
            new Rule("POST", "/api/config/agent-api", "config:manage"));

    public Optional<String> permissionFor(String method, String path) {
        for (Rule rule : rules) {
            boolean methodMatches = "*".equals(rule.method()) || rule.method().equalsIgnoreCase(method);
            if (methodMatches && matcher.match(rule.pattern(), path)) {
                return Optional.of(rule.permission());
            }
        }
        return Optional.empty();
    }
}
