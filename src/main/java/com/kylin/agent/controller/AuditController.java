package com.kylin.agent.controller;

import com.kylin.agent.audit.AuditService;
import com.kylin.agent.web.ApiResponse;
import com.kylin.agent.web.AppException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
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
    public Object recent(HttpServletRequest request) {
        int limit = parseLimit(request.getParameter("limit"));
        List<?> events = auditService.recent(limit);
        if (request.getRequestURI().startsWith("/api/v1/")) {
            return ApiResponse.ok(events);
        }
        return events;
    }

    @GetMapping({"/api/audit/verify", "/api/v1/audit/verify"})
    public Object verify(HttpServletRequest request) {
        Map<String, Object> result = auditService.verifyChain();
        if (request.getRequestURI().startsWith("/api/v1/")) {
            return ApiResponse.ok(result);
        }
        return result;
    }

    private int parseLimit(String raw) {
        if (raw == null || raw.isBlank()) {
            return 50;
        }
        try {
            return Math.min(Math.max(Integer.parseInt(raw.trim()), 1), 1000);
        } catch (NumberFormatException e) {
            throw AppException.validation("limit must be an integer");
        }
    }
}
