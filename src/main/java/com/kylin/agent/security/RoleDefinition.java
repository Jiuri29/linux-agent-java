package com.kylin.agent.security;

import java.util.List;

public record RoleDefinition(String name, List<String> permissions) {

    public RoleDefinition {
        name = name == null ? "" : name.trim();
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
    }
}
