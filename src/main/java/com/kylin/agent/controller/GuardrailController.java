package com.kylin.agent.controller;

import com.kylin.agent.service.GuardrailService;
import com.kylin.agent.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class GuardrailController {

    private final GuardrailService guardrailService;

    public GuardrailController(GuardrailService guardrailService) {
        this.guardrailService = guardrailService;
    }

    @GetMapping({"/api/guardrails/rules", "/api/v1/guardrails/rules"})
    public Object rules(HttpServletRequest request) {
        Object result = guardrailService.rules();
        return request.getRequestURI().startsWith("/api/v1/") ? ApiResponse.ok(result) : result;
    }

    @PostMapping({"/api/guardrails/rules", "/api/v1/guardrails/rules"})
    public Map<String, Object> saveRule(@RequestBody Map<String, Object> body) {
        var rule = guardrailService.saveRule(body);
        return Map.of(
                "status", "ok",
                "id", rule.id,
                "name", rule.name,
                "action", rule.action);
    }

    @PostMapping("/api/guardrails/rules/status")
    public Map<String, Object> setStatus(@RequestBody Map<String, Object> body) {
        var rule = guardrailService.setStatus(body);
        return Map.of("status", "ok", "id", rule.id, "enabled", rule.enabled);
    }

    @PostMapping("/api/guardrails/rules/test")
    public Map<String, Object> testRule(@RequestBody Map<String, Object> body) {
        return guardrailService.testRule(body);
    }

    @GetMapping("/api/guardrails/exemptions")
    public Object exemptions(HttpServletRequest request) {
        Object result = guardrailService.exemptions();
        return request.getRequestURI().startsWith("/api/v1/") ? ApiResponse.ok(result) : result;
    }

    @PostMapping("/api/guardrails/exemptions")
    public Map<String, Object> addExemption(@RequestBody Map<String, Object> body) {
        var exemption = guardrailService.addExemption(body);
        return Map.of(
                "status", "ok",
                "id", exemption.id(),
                "pattern", exemption.pattern());
    }

    @PostMapping({"/api/guardrails/evaluate", "/api/v1/guardrails/evaluate"})
    public Object evaluate(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String intent = body.get("intent") == null ? "" : String.valueOf(body.get("intent"));
        @SuppressWarnings("unchecked")
        List<String> commands = body.get("commands") instanceof List ? (List<String>) body.get("commands") : List.of();
        if (body.get("command") instanceof String single && commands.isEmpty()) {
            commands = List.of(single);
        }
        Map<String, Object> result = guardrailService.evaluate(intent, commands);
        return request.getRequestURI().startsWith("/api/v1/") ? ApiResponse.ok(result) : result;
    }

    @GetMapping({"/api/guardrails/approvals", "/api/v1/guardrails/approvals"})
    public Object approvals(@RequestParam(value = "limit", defaultValue = "50") int limit,
                            HttpServletRequest request) {
        Object result = guardrailService.approvals(limit);
        return request.getRequestURI().startsWith("/api/v1/") ? ApiResponse.ok(result) : result;
    }
}
