package com.linux.agent.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;

public record ChangeRecord(
        String id,
        @JsonProperty("service_id") String serviceId,
        Instant ts,
        String type,
        Map<String, Object> detail,
        String operator) {
}
