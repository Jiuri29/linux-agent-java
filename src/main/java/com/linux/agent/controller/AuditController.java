package com.linux.agent.controller;

import com.linux.agent.audit.AuditService;
import com.linux.agent.web.ApiResponse;
import com.linux.agent.web.AppException;
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
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping({"/api/audit/recent", "/api/v1/audit/recent"})
    public Object recent(@RequestParam(value = "limit", defaultValue = "50") int limit,
                         HttpServletRequest request) {
        List<?> events = auditService.recent(Math.min(Math.max(limit, 1), 1000));
        return request.getRequestURI().startsWith("/api/v1/") ? ApiResponse.ok(events) : events;
    }

    @GetMapping({"/api/audit/query", "/api/v1/audit/query"})
    public Object query(@RequestParam(value = "user", required = false) String user,
                        @RequestParam(value = "asset", required = false) String asset,
                        @RequestParam(value = "action", required = false) String action,
                        @RequestParam(value = "risk", required = false) String risk,
                        @RequestParam(value = "event_type", required = false) String eventType,
                        @RequestParam(value = "keyword", required = false) String keyword,
                        @RequestParam(value = "from", required = false) String from,
                        @RequestParam(value = "to", required = false) String to,
                        HttpServletRequest request) {
        List<?> events = auditService.query(user, asset, action, risk, eventType, keyword, from, to);
        return request.getRequestURI().startsWith("/api/v1/") ? ApiResponse.ok(events) : events;
    }

    @GetMapping("/api/audit/export")
    public ResponseEntity<String> export(
            @RequestParam(value = "user", required = false) String user,
            @RequestParam(value = "asset", required = false) String asset,
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "risk", required = false) String risk,
            @RequestParam(value = "event_type", required = false) String eventType,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to) {
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"audit-export.jsonl\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(auditService.exportText(user, asset, action, risk, eventType, keyword, from, to));
    }

    @GetMapping({"/api/audit/verify", "/api/v1/audit/verify"})
    public Object verify(HttpServletRequest request) {
        Map<String, Object> result = auditService.verifyChain();
        return request.getRequestURI().startsWith("/api/v1/") ? ApiResponse.ok(result) : result;
    }

    @PostMapping({"/api/audit/archive", "/api/v1/audit/archive"})
    public Object archive(@RequestBody(required = false) Map<String, Object> body,
                          HttpServletRequest request) {
        int days = 90;
        if (body != null && body.get("days") instanceof Number n) {
            days = Math.min(Math.max(n.intValue(), 1), 3650);
        }
        Map<String, Object> result = auditService.archive(days);
        return request.getRequestURI().startsWith("/api/v1/") ? ApiResponse.ok(result) : result;
    }

    @GetMapping({"/api/audit/forensics", "/api/v1/audit/forensics"})
    public Object forensics(HttpServletRequest request) {
        Map<String, Object> result = auditService.forensics();
        return request.getRequestURI().startsWith("/api/v1/") ? ApiResponse.ok(result) : result;
    }

    @GetMapping({"/api/audit/reports/compliance", "/api/v1/audit/reports/compliance"})
    public Object compliance(@RequestParam(value = "days", defaultValue = "30") int days,
                             HttpServletRequest request) {
        Map<String, Object> result = auditService.compliance(Math.min(Math.max(days, 1), 3650));
        return request.getRequestURI().startsWith("/api/v1/") ? ApiResponse.ok(result) : result;
    }

    @GetMapping({"/api/audit/retention", "/api/v1/audit/retention"})
    public Object retention(@RequestParam(value = "days", defaultValue = "90") int days,
                            HttpServletRequest request) {
        Map<String, Object> result = auditService.retention(Math.min(Math.max(days, 1), 3650));
        return request.getRequestURI().startsWith("/api/v1/") ? ApiResponse.ok(result) : result;
    }
}
