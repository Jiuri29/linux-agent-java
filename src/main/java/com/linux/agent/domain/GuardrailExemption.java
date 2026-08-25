package com.linux.agent.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record GuardrailExemption(
        String id,
        String pattern,
        String reason,
        String operator,
        @JsonProperty("until_epoch_ms") Long untilEpochMs,
        Instant created_at) {

    public boolean active(long nowEpochMs) {
        return untilEpochMs == null || untilEpochMs > nowEpochMs;
    }
}
