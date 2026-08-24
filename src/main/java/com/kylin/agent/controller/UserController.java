package com.kylin.agent.controller;

import com.kylin.agent.service.UserService;
import com.kylin.agent.web.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class UserController {

    public record CreateUserRequest(
            @NotBlank String username,
            @NotBlank String password,
            String display_name,
            String role) {
    }

    public record UpdateUserRequest(
            @NotBlank String username,
            String display_name,
            List<String> roles) {
    }

    public record StatusRequest(
            @NotBlank String username,
            @NotBlank String status) {
    }

    public record ResetPasswordRequest(
            @NotBlank String username,
            @NotBlank String password) {
    }

    public record RevokeSessionsRequest(@NotBlank String username) {
    }

    public record UpsertRoleRequest(
            @NotBlank String name,
            List<String> permissions) {
    }

    public record DeleteRoleRequest(@NotBlank String name) {
    }

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping({"/api/users", "/api/v1/users"})
    public Object list(jakarta.servlet.http.HttpServletRequest request) {
        List<Map<String, Object>> users = userService.listUsers();
        return request.getRequestURI().startsWith("/api/v1/") ? ApiResponse.ok(users) : users;
    }

    @PostMapping("/api/users")
    public Map<String, Object> create(@Valid @RequestBody CreateUserRequest request) {
        var account = userService.create(request.username(), request.password(),
                request.display_name(), request.role());
        return Map.of("status", "ok", "username", account.getUsername());
    }

    @PostMapping("/api/users/update")
    public Map<String, Object> update(@Valid @RequestBody UpdateUserRequest request) {
        userService.update(request.username(), request.display_name(), request.roles());
        return Map.of("status", "ok");
    }

    @PostMapping("/api/users/status")
    public Map<String, Object> status(@Valid @RequestBody StatusRequest request) {
        userService.setStatus(request.username(), request.status());
        return Map.of("status", "ok");
    }

    @PostMapping("/api/users/reset-password")
    public Map<String, Object> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(request.username(), request.password());
        return Map.of("status", "ok");
    }

    @PostMapping("/api/users/revoke-sessions")
    public Map<String, Object> revokeSessions(@Valid @RequestBody RevokeSessionsRequest request) {
        int removed = userService.revokeSessions(request.username());
        return Map.of("status", "ok", "revoked", removed);
    }

    @GetMapping({"/api/roles", "/api/v1/roles"})
    public Object roles(jakarta.servlet.http.HttpServletRequest request) {
        List<Map<String, Object>> roles = userService.listRoles();
        return request.getRequestURI().startsWith("/api/v1/") ? ApiResponse.ok(roles) : roles;
    }

    @PostMapping("/api/roles/update")
    public Map<String, Object> upsertRole(@Valid @RequestBody UpsertRoleRequest request) {
        var role = userService.upsertRole(request.name(), request.permissions());
        return Map.of("status", "ok", "name", role.name());
    }

    @PostMapping("/api/roles/delete")
    public Map<String, Object> deleteRole(@Valid @RequestBody DeleteRoleRequest request) {
        userService.deleteRole(request.name());
        return Map.of("status", "ok");
    }
}
