package com.kylin.agent.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.kylin.agent.service.McpService;
import com.kylin.agent.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class McpController {

    private final McpService mcpService;

    public McpController(McpService mcpService) {
        this.mcpService = mcpService;
    }

    @GetMapping({"/api/mcp/tools", "/api/v1/mcp/tools"})
    public Object tools(HttpServletRequest request) {
        Object result = mcpService.tools();
        return request.getRequestURI().startsWith("/api/v1/") ? ApiResponse.ok(result) : result;
    }

    @GetMapping({"/api/mcp/capabilities", "/api/v1/mcp/capabilities"})
    public Object capabilities(HttpServletRequest request) {
        Object result = mcpService.capabilities();
        return request.getRequestURI().startsWith("/api/v1/") ? ApiResponse.ok(result) : result;
    }

    @PostMapping({"/api/mcp/invoke", "/api/v1/mcp/invoke"})
    public Object invoke(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        @SuppressWarnings("unchecked")
        Map<String, Object> params = body.get("params") instanceof Map
                ? (Map<String, Object>) body.get("params") : Map.of();
        Map<String, Object> result = mcpService.invoke(
                String.valueOf(body.get("name")), params);
        return request.getRequestURI().startsWith("/api/v1/") ? ApiResponse.ok(result) : result;
    }

    @PostMapping({"/api/mcp/rpc", "/api/v1/mcp/rpc"})
    public Object rpc(@RequestBody JsonNode body, HttpServletRequest request) {
        Object result = mcpService.rpc(body);
        return request.getRequestURI().startsWith("/api/v1/") ? ApiResponse.ok(result) : result;
    }

    @GetMapping({"/api/mcp/plugins", "/api/v1/mcp/plugins"})
    public Object plugins(HttpServletRequest request) {
        Object result = mcpService.plugins();
        return request.getRequestURI().startsWith("/api/v1/") ? ApiResponse.ok(result) : result;
    }

    @PostMapping("/api/mcp/plugins/register")
    public Map<String, Object> registerPlugin(@RequestBody Map<String, Object> body) {
        var plugin = mcpService.registerPlugin(body);
        return Map.of(
                "status", "ok",
                "id", plugin.id,
                "name", plugin.name,
                "provided_tools", List.copyOf(plugin.providedTools.stream()
                        .map(t -> t.name).toList()));
    }

    @PostMapping("/api/mcp/plugins/status")
    public Map<String, Object> setPluginStatus(@RequestBody Map<String, Object> body) {
        return mcpService.setPluginStatus(body);
    }

    @PostMapping("/api/mcp/tools/status")
    public Map<String, Object> setToolStatus(@RequestBody Map<String, Object> body) {
        return mcpService.setToolStatus(body);
    }

    @PostMapping("/api/mcp/plugins/uninstall")
    public Map<String, Object> uninstallPlugin(@RequestBody Map<String, Object> body) {
        return mcpService.uninstallPlugin(body);
    }
}
