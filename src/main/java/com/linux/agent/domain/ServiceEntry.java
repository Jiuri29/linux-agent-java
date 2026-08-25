package com.linux.agent.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public class ServiceEntry {

    public String id;
    public String name;
    @JsonProperty("asset_id")
    public String assetId;
    public String unit;
    @JsonProperty("config_path")
    public String configPath;
    public String description;
    @JsonProperty("desired_state")
    public String desiredState = "running";
    @JsonProperty("created_at")
    public Instant createdAt;
    @JsonProperty("updated_at")
    public Instant updatedAt;
}
