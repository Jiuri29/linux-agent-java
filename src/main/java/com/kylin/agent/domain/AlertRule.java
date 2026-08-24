package com.kylin.agent.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AlertRule {

    public String id;
    public String name;
    public String metric;
    public String operator = ">=";
    public double threshold;
    public String severity = "warning";
    @JsonProperty("duration_seconds")
    public int durationSeconds;
    public boolean enabled = true;
}
