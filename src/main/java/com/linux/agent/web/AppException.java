package com.linux.agent.web;

import java.util.Map;

public class AppException extends RuntimeException {

    private final ErrorCode code;
    private final Map<String, Object> data;

    public AppException(ErrorCode code, String message) {
        this(code, message, Map.of());
    }

    public AppException(ErrorCode code, String message, Map<String, Object> data) {
        super(message);
        this.code = code;
        this.data = data == null ? Map.of() : Map.copyOf(data);
    }

    public ErrorCode code() {
        return code;
    }

    public Map<String, Object> data() {
        return data;
    }

    public static AppException validation(String message) {
        return new AppException(ErrorCode.VALIDATION_ERROR, message);
    }

    public static AppException notFound(String message) {
        return new AppException(ErrorCode.NOT_FOUND, message);
    }

    public static AppException unauthenticated(String message) {
        return new AppException(ErrorCode.UNAUTHENTICATED, message);
    }

    public static AppException permissionDenied(String requiredPermission) {
        return new AppException(ErrorCode.PERMISSION_DENIED, "permission denied",
                Map.of("required_permission", requiredPermission));
    }

    public static AppException csrfRequired() {
        return new AppException(ErrorCode.CSRF_REQUIRED, "csrf token missing or invalid");
    }
}
