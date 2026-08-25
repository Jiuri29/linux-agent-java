package com.linux.agent;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MonitorAlertTests extends AbstractApiTest {

    private static final String CPU_RULE = "demo_cpu_hot";
    private static final String ALWAYS_RULE = "alw_memory_low";

    @Test
    @Order(1)
    void saveRulesAndSimulate() throws Exception {
        AuthSession admin = adminSession();

        mockMvc.perform(post("/api/monitor/rules").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of("rule", Map.of(
                                "id", CPU_RULE,
                                "name", "演示 CPU 过高",
                                "metric", "cpu.usage_percent",
                                "operator", ">=",
                                "threshold", 80,
                                "severity", "critical"))))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(CPU_RULE));

        mockMvc.perform(get("/api/monitor/alert-rules").cookie(admin.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + CPU_RULE + "')]").isNotEmpty());

        mockMvc.perform(post("/api/monitor/simulate").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of("metrics", Map.of("cpu.usage_percent", 95)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].rule_id").value(CPU_RULE));

        mockMvc.perform(post("/api/monitor/simulate").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of("metrics", Map.of("cpu.usage_percent", 50)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @Order(2)
    void snapshotFiresAcknowledgeRecoversAlert() throws Exception {
        AuthSession admin = adminSession();

        mockMvc.perform(post("/api/monitor/alert-rules").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of("rule", Map.of(
                                "id", ALWAYS_RULE,
                                "name", "内存告警(测试)",
                                "metric", "memory.usage_percent",
                                "operator", ">=",
                                "threshold", -1,
                                "severity", "warning"))))
                )
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/monitor/snapshot").cookie(admin.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alerts_fired").value(
                        org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        MvcResult alertsResult = mockMvc.perform(get("/api/monitor/alerts")
                        .cookie(admin.cookie()).queryParam("status", "active"))
                .andExpect(status().isOk())
                .andReturn();
        java.util.List<String> alertIds = com.jayway.jsonpath.JsonPath.read(
                alertsResult.getResponse().getContentAsString(),
                "$[?(@.rule_id == '" + ALWAYS_RULE + "')].id");
        org.junit.jupiter.api.Assertions.assertFalse(alertIds.isEmpty(), "expected active alert for " + ALWAYS_RULE);
        String alertId = alertIds.get(0);

        mockMvc.perform(post("/api/monitor/alerts/acknowledge").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of("id", alertId))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/monitor/alerts/recover").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of("id", alertId))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/monitor/notifications").cookie(admin.cookie()))
                .andExpect(jsonPath("$.length()").value(
                        org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        mockMvc.perform(get("/api/monitor/latest").cookie(admin.cookie()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/monitor/history").cookie(admin.cookie())
                        .queryParam("limit", "10"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(3)
    void suppressionBlocksNewAlerts() throws Exception {
        AuthSession admin = adminSession();
        String ruleId = "sup_metric_rule";

        mockMvc.perform(post("/api/monitor/rules").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of("rule", Map.of(
                                "id", ruleId,
                                "name", "被抑制的规则",
                                "metric", "disk.usage_percent_max",
                                "operator", ">=",
                                "threshold", -1,
                                "severity", "warning"))))
                )
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/monitor/suppressions").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of("metric", "disk.usage_percent_max", "reason", "test"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty());

        mockMvc.perform(get("/api/monitor/snapshot").cookie(admin.cookie()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/monitor/alerts").cookie(admin.cookie())
                        .queryParam("status", "active"))
                .andExpect(jsonPath("$[?(@.rule_id == '" + ruleId + "')]").isEmpty());
    }

    @Test
    @Order(4)
    void osSnapshotAndRiskScan() throws Exception {
        AuthSession admin = adminSession();

        mockMvc.perform(get("/api/os/read-only-snapshot").cookie(admin.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hostname").isNotEmpty())
                .andExpect(jsonPath("$.memory.total_kb").isNotEmpty());

        mockMvc.perform(get("/api/risk/scan").cookie(admin.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.findings").isArray());
    }
}
