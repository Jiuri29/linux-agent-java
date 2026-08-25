package com.linux.agent.controller;

import com.linux.agent.service.ServiceCenterService;
import com.linux.agent.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class ServiceCenterController {

    private final ServiceCenterService service;

    public ServiceCenterController(ServiceCenterService service) {
        this.service = service;
    }

    @GetMapping({"/api/services", "/api/v1/services"})
    public Object list(HttpServletRequest request) {
        Object result = service.list();
        return request.getRequestURI().startsWith("/api/v1/") ? ApiResponse.ok(result) : result;
    }

    @PostMapping("/api/services")
    public Map<String, Object> upsert(@RequestBody Map<String, Object> body) {
        return service.upsert(body);
    }

    @GetMapping({"/api/services/detail", "/api/v1/services/detail"})
    public Object detail(@RequestParam("id") String id, HttpServletRequest request) {
        Map<String, Object> result = service.detail(id);
        return request.getRequestURI().startsWith("/api/v1/") ? ApiResponse.ok(result) : result;
    }

    @PostMapping("/api/services/lifecycle")
    public Map<String, Object> lifecycle(@RequestBody Map<String, Object> body) {
        return service.lifecycle(body);
    }

    @GetMapping("/api/services/config/read")
    public Map<String, Object> readConfig(@RequestParam("service_id") String serviceId) {
        return service.readConfig(serviceId);
    }

    @GetMapping("/api/services/config/versions")
    public Object versions(@RequestParam("service_id") String serviceId,
                           HttpServletRequest request) {
        Object result = service.versions(serviceId);
        return request.getRequestURI().startsWith("/api/v1/") ? ApiResponse.ok(result) : result;
    }

    @PostMapping("/api/services/config/versions")
    public Map<String, Object> saveVersion(@RequestBody Map<String, Object> body) {
        var version = service.saveVersion(body);
        return Map.of(
                "status", "ok",
                "id", version.id(),
                "service_id", version.serviceId(),
                "created_at", String.valueOf(version.createdAt()));
    }

    @GetMapping("/api/services/config/versions/detail")
    public Object versionDetail(@RequestParam("id") String id, HttpServletRequest request) {
        Object version = service.versionDetail(id);
        return request.getRequestURI().startsWith("/api/v1/") ? ApiResponse.ok(version) : version;
    }

    @GetMapping("/api/services/config/diff")
    public Map<String, Object> diff(@RequestParam("from") String from,
                                    @RequestParam("to") String to) {
        return service.diff(from, to);
    }

    @GetMapping({"/api/services/config/drift", "/api/v1/services/config/drift"})
    public Object drift(@RequestParam("service_id") String serviceId,
                        @RequestParam(value = "version_id", required = false) String versionId,
                        HttpServletRequest request) {
        Map<String, Object> result = service.drift(serviceId, versionId);
        return request.getRequestURI().startsWith("/api/v1/") ? ApiResponse.ok(result) : result;
    }

    @PostMapping("/api/services/config/rollback")
    public Map<String, Object> rollback(@RequestBody Map<String, Object> body) {
        return service.rollback(body);
    }

    @GetMapping("/api/services/changes")
    public Object changes(@RequestParam(value = "service_id", required = false) String serviceId,
                          @RequestParam(value = "limit", defaultValue = "50") int limit,
                          HttpServletRequest request) {
        Object result = service.changes(serviceId, Math.min(Math.max(limit, 1), 500));
        return request.getRequestURI().startsWith("/api/v1/") ? ApiResponse.ok(result) : result;
    }
}
