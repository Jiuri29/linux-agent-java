package com.kylin.agent.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public class TerminalTemplate {

    public String id;
    public String name;
    public String description;
    @JsonProperty("command_template")
    public String commandTemplate;
    @JsonProperty("created_at")
    public Instant createdAt;
}
