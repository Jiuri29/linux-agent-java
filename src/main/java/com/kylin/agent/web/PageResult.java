package com.kylin.agent.web;

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

public record PageResult<T>(List<T> items, long total, int page, int size) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 200;

    public static <E> PageResult<E> of(List<E> all, HttpServletRequest request) {
        int page = intValue(request, "page", 1, 1, Integer.MAX_VALUE);
        int size = intValue(request, "size", DEFAULT_SIZE, 1, MAX_SIZE);
        int fromIndex = Math.min((page - 1) * size, all.size());
        int toIndex = Math.min(fromIndex + size, all.size());
        return new PageResult<>(List.copyOf(all.subList(fromIndex, toIndex)), all.size(), page, size);
    }

    private static int intValue(HttpServletRequest request, String name, int defaultValue, int min, int max) {
        String raw = request.getParameter(name);
        if (raw == null || raw.isBlank()) {
            return Math.min(Math.max(defaultValue, min), max);
        }
        try {
            int value = Integer.parseInt(raw.trim());
            return Math.min(Math.max(value, min), max);
        } catch (NumberFormatException e) {
            throw AppException.validation(name + " must be an integer");
        }
    }
}
