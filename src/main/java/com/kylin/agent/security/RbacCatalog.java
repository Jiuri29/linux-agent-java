package com.kylin.agent.security;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RbacCatalog {

    public static final String WILDCARD = "*";

    public static final Map<String, List<String>> ROLE_PERMISSIONS = Map.of(
            "super_admin", List.of(WILDCARD),
            "admin", List.of("asset:read", "asset:edit", "service:read", "service:edit",
                    "incident:read", "incident:edit", "monitor:read", "monitor:edit",
                    "audit:read", "file:read", "terminal:run", "guardrail:evaluate",
                    "executor:prepare", "mcp:invoke", "chatops:send",
                    "member:manage", "role:manage", "config:read", "config:edit", "test:run"),
            "operator", List.of("asset:read", "asset:edit", "service:read", "service:edit",
                    "incident:read", "incident:edit", "monitor:read", "monitor:edit",
                    "file:read", "terminal:run", "guardrail:evaluate",
                    "executor:prepare", "mcp:invoke", "chatops:send", "test:run"),
            "auditor", List.of("monitor:read", "audit:read", "asset:read"),
            "viewer", List.of("asset:read", "monitor:read", "audit:read"));

    private RbacCatalog() {
    }

    public static Set<String> permissionsForRoles(Collection<String> roles) {
        Set<String> permissions = new LinkedHashSet<>();
        if (roles != null) {
            for (String role : roles) {
                List<String> granted = ROLE_PERMISSIONS.get(role);
                if (granted != null) {
                    permissions.addAll(granted);
                }
            }
        }
        return permissions;
    }

    public static boolean hasPermission(Set<String> permissions, String required) {
        if (required == null) {
            return true;
        }
        return permissions.contains(WILDCARD) || permissions.contains(required);
    }
}
