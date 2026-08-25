package com.linux.agent.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record ConfigVersion(
        String id,
        @JsonProperty("service_id") String serviceId,
        @JsonProperty("config_path") String configPath,
        String content,
        String comment,
        @JsonProperty("created_by") String createdBy,
        @JsonProperty("created_at") Instant createdAt) {
}
