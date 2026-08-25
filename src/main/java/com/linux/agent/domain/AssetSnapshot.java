package com.linux.agent.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;

public record AssetSnapshot(
        @JsonProperty("asset_id") String assetId,
        @JsonProperty("host_id") String hostId,
        @JsonProperty("captured_at") Instant capturedAt,
        Map<String, Object> data) {
}
