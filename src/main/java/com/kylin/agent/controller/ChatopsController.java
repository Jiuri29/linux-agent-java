package com.kylin.agent.controller;

import com.kylin.agent.service.ChatopsService;
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
public class ChatopsController {

    private final ChatopsService chatopsService;

    public ChatopsController(ChatopsService chatopsService) {
        this.chatopsService = chatopsService;
    }

    @GetMapping({"/api/chatops/templates", "/api/v1/chatops/templates"})
    public Object templates(HttpServletRequest request) {
        Object result = chatopsService.templates();
        return request.getRequestURI().startsWith("/api/v1/") ? ApiResponse.ok(result) : result;
    }

    @PostMapping({"/api/chatops/message", "/api/v1/chatops/message"})
    public Object message(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Map<String, Object> result = chatopsService.sendMessage(
                body.get("session_id") == null ? null : String.valueOf(body.get("session_id")),
                body.get("message") == null ? null : String.valueOf(body.get("message")));
        return request.getRequestURI().startsWith("/api/v1/") ? ApiResponse.ok(result) : result;
    }

    @PostMapping("/api/chatops/confirm")
    public Map<String, Object> confirm(@RequestBody Map<String, Object> body) {
        return chatopsService.confirm(
                body.get("session_id") == null ? null : String.valueOf(body.get("session_id")),
                body.get("action_id") == null ? null : String.valueOf(body.get("action_id")));
    }

    @GetMapping({"/api/chatops/session", "/api/v1/chatops/session"})
    public Object session(@RequestParam("id") String id, HttpServletRequest request) {
        Map<String, Object> result = chatopsService.sessionDetail(id);
        return request.getRequestURI().startsWith("/api/v1/") ? ApiResponse.ok(result) : result;
    }

    @PostMapping({"/api/chatops/demo-cleanup", "/api/v1/chatops/demo-cleanup"})
    public Object demoCleanup(HttpServletRequest request) {
        Map<String, Object> result = chatopsService.demoCleanup();
        return request.getRequestURI().startsWith("/api/v1/") ? ApiResponse.ok(result) : result;
    }
}
