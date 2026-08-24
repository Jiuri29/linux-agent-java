package com.kylin.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ops-agent")
public record AppProperties(
        String dataDir,
        String auditLog,
        String authStore,
        String assetStore,
        String serviceCenterStore,
        String incidentStore,
        String apiConfig,
        String executionStore,
        String guardrailsStore,
        String guardrailsApprovalStore,
        String terminalLogDir,
        String orchestrationDir,
        String mcpStore,
        String mcpInvocationLog,
        String chatopsStore,
        String alertRulesConfig,
        String alertStateStore,
        String metricsHistoryStore,
        String latestSnapshotStore,
        String testRunDir,
        String frontendDir) {

    public AppProperties {
        String base = normalizeBase(isBlank(dataDir) ? ".runtime" : dataDir);
        dataDir = base;
        auditLog = orDefault(auditLog, base + "/audit/events.jsonl");
        authStore = orDefault(authStore, base + "/auth/users.json");
        assetStore = orDefault(assetStore, base + "/assets/assets.json");
        serviceCenterStore = orDefault(serviceCenterStore, base + "/services/service-center.json");
        incidentStore = orDefault(incidentStore, base + "/incidents/incidents.json");
        apiConfig = orDefault(apiConfig, base + "/config/agent-api.json");
        executionStore = orDefault(executionStore, base + "/executor/pending.jsonl");
        guardrailsStore = orDefault(guardrailsStore, base + "/guardrails/rules.json");
        guardrailsApprovalStore = orDefault(guardrailsApprovalStore, base + "/guardrails/approvals.jsonl");
        terminalLogDir = orDefault(terminalLogDir, base + "/terminal");
        orchestrationDir = orDefault(orchestrationDir, base + "/orchestration");
        mcpStore = orDefault(mcpStore, base + "/mcp/registry.json");
        mcpInvocationLog = orDefault(mcpInvocationLog, base + "/mcp/invocations.jsonl");
        chatopsStore = orDefault(chatopsStore, base + "/chatops/sessions.json");
        alertRulesConfig = orDefault(alertRulesConfig, base + "/monitor/alert-rules.json");
        alertStateStore = orDefault(alertStateStore, base + "/monitor/alert-state.json");
        metricsHistoryStore = orDefault(metricsHistoryStore, base + "/monitor/metrics.jsonl");
        latestSnapshotStore = orDefault(latestSnapshotStore, base + "/monitor/latest-snapshot.json");
        testRunDir = orDefault(testRunDir, base + "/test-runs");
        frontendDir = orDefault(frontendDir, "frontend/build");
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String orDefault(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value;
    }

    private static String normalizeBase(String s) {
        String trimmed = s.trim();
        while (trimmed.endsWith("/") || trimmed.endsWith("\\")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
