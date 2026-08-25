package com.kylin.agent.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.kylin.agent.service.MetricsService;

import java.io.IOException;

@Component
@Order(2)
public class MetricsFilter extends OncePerRequestFilter {

    private final MetricsService metricsService;

    public MetricsFilter(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } finally {
            if (!"/metrics".equals(request.getRequestURI())) {
                String path = request.getRequestURI();
                metricsService.record(request.getMethod(), path,
                        String.valueOf(response.getStatus()));
            }
        }
    }
}
