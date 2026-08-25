package com.linux.agent.controller;

import com.linux.agent.service.AgentConfigService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class AgentConfigController {

    private final AgentConfigService agentConfigService;

    public AgentConfigController(AgentConfigService agentConfigService) {
        this.agentConfigService = agentConfigService;
    }

    @GetMapping({"/api/config/agent-api", "/api/v1/config/agent-api"})
    public Map<String, Object> get() {
        return agentConfigService.get();
    }

    @PostMapping({"/api/config/agent-api", "/api/v1/config/agent-api"})
    public Map<String, Object> save(@RequestBody Map<String, Object> body) {
        return agentConfigService.save(body);
    }

    @PostMapping("/api/config/agent-api/test")
    public Map<String, Object> test() {
        return agentConfigService.test();
    }
}
