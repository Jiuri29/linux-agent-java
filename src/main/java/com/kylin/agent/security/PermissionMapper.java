package com.kylin.agent.security;

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
            new Rule("*", "/api/assets*", "asset:read"),
            new Rule("POST", "/api/assets*", "asset:edit"),
            new Rule("PUT", "/api/assets/**", "asset:edit"),
            new Rule("DELETE", "/api/assets/**", "asset:edit"),
            new Rule("*", "/api/services*", "service:read"),
            new Rule("POST", "/api/services*", "service:edit"),
            new Rule("*", "/api/incidents*", "incident:read"),
            new Rule("POST", "/api/incidents*", "incident:edit"),
            new Rule("*", "/api/work-orders*", "incident:read"),
            new Rule("POST", "/api/work-orders*", "incident:edit"),
            new Rule("*", "/api/monitor/*", "monitor:read"),
            new Rule("POST", "/api/monitor/*", "monitor:edit"),
            new Rule("GET", "/api/audit/*", "audit:read"),
            new Rule("GET", "/api/files/*", "file:read"),
            new Rule("POST", "/api/terminal/run", "terminal:run"),
            new Rule("POST", "/api/guardrails/evaluate", "guardrail:evaluate"),
            new Rule("POST", "/api/executor/prepare", "executor:prepare"),
            new Rule("POST", "/api/mcp/invoke", "mcp:invoke"),
            new Rule("POST", "/api/chatops/message", "chatops:send"),
            new Rule("*", "/api/users*", "member:manage"),
            new Rule("*", "/api/roles*", "role:manage"),
            new Rule("GET", "/api/config/agent-api", "config:read"),
            new Rule("POST", "/api/config/agent-api", "config:edit"),
            new Rule("POST", "/api/tests/run", "test:run"));

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
