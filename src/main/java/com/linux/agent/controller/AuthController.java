package com.linux.agent.controller;

import com.linux.agent.security.AuthInterceptor;
import com.linux.agent.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record InitAdminRequest(
            @NotBlank String username,
            @NotBlank String password,
            String displayName) {
    }

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password) {
    }

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/bootstrap")
    public Map<String, Object> bootstrap() {
        return Map.of("initialized", authService.initialized());
    }

    @PostMapping("/init-admin")
    public Map<String, Object> initAdmin(@Valid @RequestBody InitAdminRequest request) {
        var account = authService.initAdmin(request.username(), request.password(), request.displayName());
        return Map.of("status", "ok", "username", account.getUsername(), "roles", account.getRoles());
    }

    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody LoginRequest request,
                                     jakarta.servlet.http.HttpServletResponse response) {
        AuthService.LoginResult result = authService.login(request.username(), request.password());
        ResponseCookie cookie = ResponseCookie.from(AuthInterceptor.SESSION_COOKIE, result.sessionToken())
                .httpOnly(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(Duration.ofHours(8))
                .build();
        response.setHeader("Set-Cookie", cookie.toString());
        return Map.of(
                "status", "ok",
                "csrf_token", result.csrfToken(),
                "user", Map.of(
                        "username", result.username(),
                        "display_name", result.displayName(),
                        "roles", result.roles()));
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletRequest request,
                                      jakarta.servlet.http.HttpServletResponse response) {
        String token = null;
        if (request.getCookies() != null) {
            for (var cookie : request.getCookies()) {
                if (AuthInterceptor.SESSION_COOKIE.equals(cookie.getName())) {
                    token = cookie.getValue();
                    break;
                }
            }
        }
        String operator = (String) request.getAttribute(AuthInterceptor.OPERATOR_ATTRIBUTE);
        authService.logout(token, operator);

        ResponseCookie cookie = ResponseCookie.from(AuthInterceptor.SESSION_COOKIE, "")
                .httpOnly(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
        response.setHeader("Set-Cookie", cookie.toString());
        return Map.of("status", "ok");
    }
}
