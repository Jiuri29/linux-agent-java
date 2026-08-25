package com.linux.agent.controller;

import com.linux.agent.service.MonitoringService;
import com.linux.agent.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class MonitorController {

    private final MonitoringService monitoringService;

    public MonitorController(MonitoringService monitoringService) {
        this.monitoringService = monitoringService;
    }

    @GetMapping({"/api/monitor/snapshot", "/api/v1/monitor/snapshot"})
    public Object snapshot(HttpServletRequest request) {
        Map<String, Object> result = monitoringService.captureAndEvaluate();
        return request.getRequestURI().startsWith("/api/v1/") ? ApiResponse.ok(result) : result;
    }

    @GetMapping({"/api/monitor/latest", "/api/v1/monitor/latest"})
    public Object latest(HttpServletRequest request) {
        Object latest = monitoringService.latest();
        return request.getRequestURI().startsWith("/api/v1/")
                ? ApiResponse.ok(latest == null ? Map.of() : latest)
                : latest;
    }

    @GetMapping({"/api/monitor/history", "/api/v1/monitor/history"})
    public Object history(@RequestParam(value = "host", required = false) String host,
                          @RequestParam(value = "metric", required = false) String metric,
                          @RequestParam(value = "limit", defaultValue = "100") int limit,
                          HttpServletRequest request) {
        List<Map<String, Object>> entries = monitoringService.history(host, metric, Math.min(Math.max(limit, 1), 1000));
        return request.getRequestURI().startsWith("/api/v1/") ? ApiResponse.ok(entries) : entries;
    }

    @GetMapping({"/api/monitor/alert-rules", "/api/v1/monitor/alert-rules"})
    public Object alertRules(HttpServletRequest request) {
        List<?> rules = monitoringService.rules();
        return request.getRequestURI().startsWith("/api/v1/") ? ApiResponse.ok(rules) : rules;
    }

    @PostMapping({"/api/monitor/alert-rules", "/api/monitor/rules"})
    public Map<String, Object> saveRules(@RequestBody Map<String, Object> body) {
        return monitoringService.saveRules(body);
    }

    @PostMapping("/api/monitor/simulate")
    public List<Map<String, Object>> simulate(@RequestBody(required = false) Map<String, Object> body) {
        return monitoringService.simulate(body == null ? Map.of() : body);
    }

    @GetMapping({"/api/monitor/alerts", "/api/v1/monitor/alerts"})
    public Object alerts(@RequestParam(value = "status", required = false) String status,
                         HttpServletRequest request) {
        List<?> alerts = monitoringService.alerts(status);
        return request.getRequestURI().startsWith("/api/v1/") ? ApiResponse.ok(alerts) : alerts;
    }

    @PostMapping("/api/monitor/alerts/acknowledge")
    public Object acknowledge(@RequestBody Map<String, Object> body) {
        return toMap(monitoringService.transition(str(body.get("id")), "acknowledge"));
    }

    @PostMapping("/api/monitor/alerts/escalate")
    public Object escalate(@RequestBody Map<String, Object> body) {
        return toMap(monitoringService.transition(str(body.get("id")), "escalate"));
    }

    @PostMapping("/api/monitor/alerts/recover")
    public Object recover(@RequestBody Map<String, Object> body) {
        return toMap(monitoringService.transition(str(body.get("id")), "recover"));
    }

    @GetMapping("/api/monitor/suppressions")
    public List<?> suppressions() {
        return monitoringService.suppressions();
    }

    @PostMapping("/api/monitor/suppressions")
    public Object addSuppression(@RequestBody Map<String, Object> body) {
        return monitoringService.addSuppression(body);
    }

    @GetMapping("/api/monitor/notifications")
    public List<?> notifications() {
        return monitoringService.notifications();
    }

    @GetMapping({"/api/os/read-only-snapshot", "/api/v1/os/read-only-snapshot"})
    public Object osSnapshot(HttpServletRequest request) {
        Map<String, Object> snapshot = monitoringService.readOnlySnapshot();
        return request.getRequestURI().startsWith("/api/v1/") ? ApiResponse.ok(snapshot) : snapshot;
    }

    @GetMapping({"/api/risk/scan", "/api/v1/risk/scan"})
    public Object riskScan(HttpServletRequest request) {
        Map<String, Object> result = monitoringService.riskScan();
        return request.getRequestURI().startsWith("/api/v1/") ? ApiResponse.ok(result) : result;
    }

    private static Map<String, Object> toMap(Object alert) {
        if (alert instanceof com.linux.agent.repository.MonitorRepository.Alert a) {
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("status", "ok");
            map.put("alert", Map.of(
                    "id", a.id,
                    "rule_id", a.ruleId,
                    "status", a.status,
                    "updated_at", String.valueOf(a.updatedAt)));
            return map;
        }
        return Map.of("status", "ok");
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
