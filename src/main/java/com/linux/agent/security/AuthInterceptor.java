package com.linux.agent.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linux.agent.web.ApiResponse;
import com.linux.agent.web.ErrorCode;
import com.linux.agent.web.ReqCtx;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String SESSION_COOKIE = "ops_session";
    public static final String CSRF_HEADER = "X-CSRF-Token";
    public static final String OPERATOR_ATTRIBUTE = "ops.operator";

    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/health",
            "/api/v1/health",
            "/api/auth/bootstrap",
            "/api/auth/init-admin",
            "/api/auth/login",
            "/error");

    private final SessionStore sessionStore;
    private final PermissionMapper permissionMapper;
    private final ObjectMapper objectMapper;

    public AuthInterceptor(SessionStore sessionStore, PermissionMapper permissionMapper, ObjectMapper objectMapper) {
        this.sessionStore = sessionStore;
        this.permissionMapper = permissionMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if (isPublic(request.getRequestURI())) {
            return true;
        }

        SessionStore.SessionInfo session = resolveSession(request);
        if (session == null) {
            writeError(response, ErrorCode.UNAUTHENTICATED, "unauthenticated", null);
            return false;
        }
        request.setAttribute(OPERATOR_ATTRIBUTE, session.username());

        if (!isSafeMethod(request.getMethod())
                && !session.csrfToken().equals(request.getHeader(CSRF_HEADER))) {
            writeError(response, ErrorCode.CSRF_REQUIRED, "csrf token missing or invalid", null);
            return false;
        }

        Optional<String> required = permissionMapper.permissionFor(request.getMethod(), normalizedPath(request.getRequestURI()));
        if (required.isPresent()) {
            Set<String> permissions = RbacCatalog.permissionsForRoles(session.roles());
            if (!RbacCatalog.hasPermission(permissions, required.get())) {
                writeError(response, ErrorCode.PERMISSION_DENIED, "permission denied",
                        java.util.Map.of("required_permission", required.get()));
                return false;
            }
        }
        return true;
    }

    private boolean isPublic(String uri) {
        for (String path : PUBLIC_PATHS) {
            if (uri.equals(path)) {
                return true;
            }
        }
        return false;
    }

    private String normalizedPath(String uri) {
        if (uri.startsWith("/api/v1/")) {
            return "/api/" + uri.substring("/api/v1/".length());
        }
        return uri;
    }

    private boolean isSafeMethod(String method) {
        return "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method) || "OPTIONS".equalsIgnoreCase(method);
    }

    public SessionStore.SessionInfo resolveSession(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (SESSION_COOKIE.equals(cookie.getName())) {
                return sessionStore.get(cookie.getValue());
            }
        }
        return null;
    }

    private void writeError(HttpServletResponse response, ErrorCode code, String message,
                            java.util.Map<String, Object> data) throws Exception {
        response.setStatus(code.httpStatus());
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(),
                ApiResponse.error(code, message, data));
    }
}
