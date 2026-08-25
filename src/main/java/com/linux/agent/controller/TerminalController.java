package com.linux.agent.controller;

import com.linux.agent.service.TerminalService;
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
public class TerminalController {

    private final TerminalService terminalService;

    public TerminalController(TerminalService terminalService) {
        this.terminalService = terminalService;
    }

    @GetMapping({"/api/terminal/templates", "/api/v1/terminal/templates"})
    public Object templates(HttpServletRequest request) {
        Object result = terminalService.templates();
        return request.getRequestURI().startsWith("/api/v1/") ? ApiResponse.ok(result) : result;
    }

    @PostMapping("/api/terminal/templates")
    public Map<String, Object> saveTemplate(@RequestBody Map<String, Object> body) {
        var template = terminalService.saveTemplate(body);
        return Map.of(
                "status", "ok",
                "id", template.id,
                "name", template.name,
                "command_template", String.valueOf(template.commandTemplate));
    }

    @PostMapping("/api/terminal/templates/render")
    public Map<String, Object> renderTemplate(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        Map<String, Object> params = body.containsKey("params") && body.get("params") instanceof Map
                ? (Map<String, Object>) body.get("params")
                : Map.of();
        return terminalService.renderTemplate(String.valueOf(body.get("template_id")), params);
    }

    @PostMapping({"/api/terminal/run", "/api/v1/terminal/run"})
    public Object run(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Map<String, Object> result = terminalService.run(body);
        return request.getRequestURI().startsWith("/api/v1/") ? ApiResponse.ok(result) : result;
    }

    @GetMapping({"/api/terminal/history", "/api/v1/terminal/history"})
    public Object history(@RequestParam(value = "limit", defaultValue = "50") int limit,
                          HttpServletRequest request) {
        List<?> result = terminalService.history(limit);
        return request.getRequestURI().startsWith("/api/v1/") ? ApiResponse.ok(result) : result;
    }

    @GetMapping({"/api/terminal/replay", "/api/v1/terminal/replay"})
    public Object replay(@RequestParam("id") String id, HttpServletRequest request) {
        Map<String, Object> result = terminalService.replay(id);
        return request.getRequestURI().startsWith("/api/v1/") ? ApiResponse.ok(result) : result;
    }
}
