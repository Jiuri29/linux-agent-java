package com.kylin.agent.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public class GuardrailRule {

    public static final String ACTION_ALLOW = "allow";
    public static final String ACTION_APPROVE = "approve";
    public static final String ACTION_DENY = "deny";

    public String id;
    public String name;
    @JsonProperty("intent_pattern")
    public String intentPattern;
    @JsonProperty("command_pattern")
    public String commandPattern;
    public String action = ACTION_APPROVE;
    public String severity = "warning";
    public boolean enabled = true;
    public boolean builtin;
    public String description;
    @JsonProperty("created_at")
    public Instant createdAt;

    public boolean matches(String intent, String command) {
        try {
            if (commandPattern != null && !commandPattern.isBlank()
                    && java.util.regex.Pattern.compile(commandPattern)
                            .matcher(command == null ? "" : command).find()) {
                return true;
            }
            if ((commandPattern == null || commandPattern.isBlank())
                    && intentPattern != null && !intentPattern.isBlank()
                    && java.util.regex.Pattern.compile(intentPattern)
                            .matcher(intent == null ? "" : intent).find()) {
                return true;
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    public boolean appliesToIntent(String intent) {
        if (intentPattern == null || intentPattern.isBlank()) {
            return true;
        }
        try {
            return java.util.regex.Pattern.compile(intentPattern)
                    .matcher(intent == null ? "" : intent).find();
        } catch (Exception e) {
            return true;
        }
    }
}
