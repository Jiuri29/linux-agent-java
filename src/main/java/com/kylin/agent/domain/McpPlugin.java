package com.kylin.agent.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class McpPlugin {

    public String id;
    public String name;
    public String description;
    public boolean enabled = true;
    @JsonProperty("provided_tools")
    public List<McpTool> providedTools = new ArrayList<>();
    @JsonProperty("registered_at")
    public Instant registeredAt;
}
