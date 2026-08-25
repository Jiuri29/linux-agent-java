package com.linux.agent.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class AgentApiConfig {

    public static final String MASK = "******";

    @JsonProperty("base_url")
    public String baseUrl;
    @JsonProperty("api_key")
    public String apiKey;
    public String model;
    public boolean enabled;
    @JsonProperty("timeout_seconds")
    public int timeoutSeconds = 20;
    @JsonProperty("allowed_hosts")
    public List<String> allowedHosts = new ArrayList<>(List.of("127.0.0.1", "localhost"));
}
