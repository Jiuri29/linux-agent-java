package com.kylin.agent.controller;

import com.kylin.agent.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        return Map.of("status", "ok");
    }

    @GetMapping("/api/v1/health")
    public ApiResponse healthV1() {
        return ApiResponse.ok(Map.of("status", "ok"));
    }
}
