package com.linux.agent.security;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RbacCatalog {

    public static final String WILDCARD = "*";

    public static final List<String> BUILTIN_ROLES = List.of(
            "super_admin", "admin", "operator", "auditor", "viewer");

    public static final Map<String, List<String>> ROLE_PERMISSIONS = buildDefaults();

    private static Map<String, List<String>> buildDefaults() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        map.put("super_admin", List.of(WILDCARD));
        map.put("admin", List.of("asset:read", "asset:edit", "asset:delete", "asset:sync",
                "monitor:read", "monitor:manage", "risk:scan",
                "service:read", "service:operate", "config:modify", "config:rollback",
                "file:read", "file:download", "file:write", "file:upload", "file:delete", "file:restore", "file:plan",
                "terminal:view", "terminal:execute", "terminal:template:manage", "terminal:replay:view",
                "guardrail:evaluate",
                "audit:read", "audit:export", "audit:verify", "audit:archive",
                "chatops:use", "mcp:invoke", "mcp:manage",
                "orchestration:read", "orchestration:execute", "orchestration:schedule",
                "test:run", "config:manage", "admin:read", "user:manage", "role:read", "role:manage"));
        map.put("operator", List.of("asset:read", "asset:edit", "asset:sync",
                "monitor:read", "risk:scan",
                "service:read", "service:operate",
                "file:read", "file:download", "file:write", "file:upload",
                "terminal:view", "terminal:execute", "terminal:template:manage", "terminal:replay:view",
                "guardrail:evaluate",
                "chatops:use", "mcp:invoke",
                "orchestration:read", "orchestration:execute", "test:run"));
        map.put("auditor", List.of("asset:read", "monitor:read", "risk:scan",
                "audit:read", "audit:export", "audit:verify"));
        map.put("viewer", List.of("asset:read", "monitor:read", "service:read",
                "file:read", "terminal:view", "audit:read"));
        return map;
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

    public static boolean isBuiltinRole(String role) {
        return BUILTIN_ROLES.contains(role);
    }
}
