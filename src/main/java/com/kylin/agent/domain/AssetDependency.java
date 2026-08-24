package com.kylin.agent.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AssetDependency(
        @JsonProperty("source_asset_id") String sourceAssetId,
        @JsonProperty("target_asset_id") String targetAssetId,
        String type) {
}
