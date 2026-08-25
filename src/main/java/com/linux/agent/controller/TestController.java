package com.linux.agent.controller;

import com.linux.agent.service.AcceptanceService;
import com.linux.agent.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class TestController {

    private final AcceptanceService acceptanceService;

    public TestController(AcceptanceService acceptanceService) {
        this.acceptanceService = acceptanceService;
    }

    @GetMapping({"/api/tests/catalog", "/api/v1/tests/catalog"})
    public Object catalog(HttpServletRequest request) {
        Object result = acceptanceService.catalog();
        return request.getRequestURI().startsWith("/api/v1/") ? ApiResponse.ok(result) : result;
    }

    @PostMapping({"/api/tests/run", "/api/v1/tests/run"})
    public Object run(@RequestBody(required = false) Map<String, Object> body,
                      HttpServletRequest request) {
        List<String> ids = body != null && body.get("ids") instanceof List
                ? ((List<?>) body.get("ids")).stream().map(String::valueOf).toList()
                : List.of();
        Map<String, Object> result = acceptanceService.run(ids, "manual");
        return request.getRequestURI().startsWith("/api/v1/") ? ApiResponse.ok(result) : result;
    }

    @PostMapping("/api/tests/agent-run")
    public Map<String, Object> agentRun(@RequestBody(required = false) Map<String, Object> body) {
        List<String> ids = body != null && body.get("ids") instanceof List
                ? ((List<?>) body.get("ids")).stream().map(String::valueOf).toList()
                : List.of();
        return acceptanceService.run(ids, "agent");
    }

    @GetMapping({"/api/tests/runs", "/api/v1/tests/runs"})
    public Object runs(@RequestParam(value = "limit", defaultValue = "20") int limit,
                       HttpServletRequest request) {
        Object result = acceptanceService.runs(Math.min(Math.max(limit, 1), 200));
        return request.getRequestURI().startsWith("/api/v1/") ? ApiResponse.ok(result) : result;
    }

    @GetMapping({"/api/tests/runs/detail", "/api/v1/tests/runs/detail"})
    public Object detail(@RequestParam("id") String id, HttpServletRequest request) {
        Map<String, Object> result = acceptanceService.detail(id);
        return request.getRequestURI().startsWith("/api/v1/") ? ApiResponse.ok(result) : result;
    }

    @GetMapping(value = {"/api/tests/runs/report", "/api/v1/tests/runs/report"},
            produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<?> report(
            @RequestParam("id") String id,
            @RequestParam(value = "v1", required = false) String v1) {
        String report = acceptanceService.report(id);
        if (Boolean.parseBoolean(v1)) {
            return ResponseEntity.ok(ApiResponse.ok(Map.of("report", report)));
        }
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_PLAIN, java.nio.charset.StandardCharsets.UTF_8))
                .body(report);
    }
}
