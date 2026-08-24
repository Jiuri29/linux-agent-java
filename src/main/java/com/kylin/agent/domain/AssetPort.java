package com.kylin.agent.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AssetPort(int port, @JsonProperty("service_name") String serviceName) {
}
