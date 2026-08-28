package com.linux.agent.service;

import com.linux.agent.audit.AuditService;
import com.linux.agent.domain.AgentApiConfig;
import com.linux.agent.repository.AgentConfigRepository;
import com.linux.agent.security.AuthInterceptor;
import com.linux.agent.web.AppException;
import com.linux.agent.web.ErrorCode;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AgentConfigService {

    private final AgentConfigRepository repository;
    private final AuditService audit;
    private final HttpClient httpClient;

    public AgentConfigService(AgentConfigRepository repository, AuditService audit) {
        this.repository = repository;
        this.audit = audit;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public synchronized Map<String, Object> get() {
        AgentApiConfig config = repository.load();
        Map<String, Object> result = new LinkedHashMap<>();
        if (config == null) {
            result.put("configured", false);
            result.put("base_url", null);
            result.put("api_key", null);
            result.put("model", null);
            result.put("enabled", false);
            result.put("timeout_seconds", 20);
            result.put("allowed_hosts", List.of("127.0.0.1", "localhost"));
            return result;
        }
        result.put("configured", true);
        result.put("base_url", config.baseUrl);
        result.put("api_key", config.apiKey == null || config.apiKey.isBlank()
                ? null : AgentApiConfig.MASK);
        result.put("model", config.model);
        result.put("enabled", config.enabled);
        result.put("timeout_seconds", config.timeoutSeconds);
        result.put("allowed_hosts", config.allowedHosts);
        return result;
    }

    public synchronized AgentApiConfig requireEnabled() {
        AgentApiConfig config = repository.load();
        if (config == null || !config.enabled || config.baseUrl == null || config.baseUrl.isBlank()) {
            throw invalidConfig("agent api is not configured or disabled");
        }
        return config;
    }

    public synchronized Map<String, Object> save(Map<String, Object> body) {
        String baseUrl = str(body.get("base_url"));
        if (baseUrl == null || baseUrl.isBlank()) {
            throw AppException.validation("base_url is required");
        }
        URI uri = parseAndValidate(baseUrl);

        List<String> allowedHosts = body.get("allowed_hosts") instanceof List<?> list
                ? list.stream().map(String::valueOf).map(String::trim).filter(s -> !s.isEmpty()).toList()
                : List.of("127.0.0.1", "localhost");
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        boolean hostAllowed = allowedHosts.stream()
                .anyMatch(h -> host.equals(h.toLowerCase(Locale.ROOT)));
        if (!hostAllowed) {
            throw new AppException(ErrorCode.INVALID_AGENT_API_CONFIG,
                    "host not in allowed_hosts: " + host,
                    Map.of("error", "host_not_allowed", "host", host));
        }

        AgentApiConfig existing = repository.load();
        AgentApiConfig config = new AgentApiConfig();
        config.baseUrl = baseUrl;
        Object apiKey = body.get("api_key");
        config.apiKey = apiKey == null || String.valueOf(apiKey).isBlank() ? null
                : AgentApiConfig.MASK.equals(String.valueOf(apiKey)) && existing != null
                ? existing.apiKey : String.valueOf(apiKey);
        config.model = str(body.get("model"));
        config.enabled = Boolean.parseBoolean(String.valueOf(body.getOrDefault("enabled", "true")));
        config.timeoutSeconds = body.get("timeout_seconds") instanceof Number n
                ? Math.min(Math.max(n.intValue(), 1), 120) : 20;
        config.allowedHosts = allowedHosts;
        repository.save(config);
        audit.append("config", operator(), "agent-api-save", "config:agent-api", "success",
                Map.of("enabled", config.enabled));
        return get();
    }

    public Map<String, Object> test() {
        AgentApiConfig config = requireEnabled();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(config.baseUrl))
                    .timeout(Duration.ofSeconds(Math.max(config.timeoutSeconds, 5)))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            boolean reachable = response.statusCode() < 500;
            audit.append("config", operator(), "agent-api-test", "config:agent-api",
                    reachable ? "success" : "failure", Map.of("status", response.statusCode()));
            if (!reachable) {
                throw invalidConfig("agent api responded with " + response.statusCode());
            }
            return Map.of("reachable", true, "status_code", response.statusCode());
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            audit.append("config", operator(), "agent-api-test", "config:agent-api", "failure",
                    Map.of("error", String.valueOf(e.getMessage())));
            throw invalidConfig("unreachable: " + e.getMessage());
        }
    }

    public String chatCompletion(String userMessage) {
        AgentApiConfig config = requireEnabled();
        try {
            String payload = "{\"message\":" + quote(userMessage) + "}";
            HttpRequest request = HttpRequest.newBuilder(URI.create(config.baseUrl))
                    .timeout(Duration.ofSeconds(Math.max(config.timeoutSeconds, 5)))
                    .header("Content-Type", "application/json")
                    .header("X-Api-Key", config.apiKey == null ? "" : config.apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw invalidConfig("agent api returned " + response.statusCode());
            }
            com.fasterxml.jackson.databind.JsonNode node =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.body());
            String reply = node.path("reply").asText(null);
            if (reply == null) {
                throw invalidConfig("agent api response missing 'reply' field");
            }
            return reply;
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw invalidConfig("unreachable: " + e.getMessage());
        }
    }

    private static String quote(String value) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);
        } catch (Exception e) {
            return "\"" + value.replace("\"", "'") + "\"";
        }
    }

    private URI parseAndValidate(String baseUrl) {
        try {
            URI uri = URI.create(baseUrl.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!List.of("http", "https").contains(scheme) || uri.getHost() == null) {
                throw invalidConfig("base_url must be a valid http(s) URL");
            }
            return uri;
        } catch (IllegalArgumentException e) {
            throw invalidConfig("base_url is not a valid URL");
        }
    }

    private static AppException invalidConfig(String message) {
        return new AppException(ErrorCode.INVALID_AGENT_API_CONFIG, message,
                Map.of("error", "invalid_agent_api_config"));
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String operator() {
        var attributes = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (attributes instanceof org.springframework.web.context.request.ServletRequestAttributes servlet) {
            Object operator = servlet.getRequest().getAttribute(AuthInterceptor.OPERATOR_ATTRIBUTE);
            if (operator != null) {
                return operator.toString();
            }
        }
        return "system";
    }
}
