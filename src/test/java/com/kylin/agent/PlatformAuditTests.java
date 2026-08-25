package com.kylin.agent;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.context.SpringBootTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PlatformAuditTests extends AbstractApiTest {

    @Test
    @Order(1)
    void runtimeContext() throws Exception {
        AuthSession admin = adminSession();

        mockMvc.perform(get("/api/runtime/context").cookie(admin.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hostname").isNotEmpty())
                .andExpect(jsonPath("$.admin_initialized").value(true))
                .andExpect(jsonPath("$.data_dir").isNotEmpty());
    }

    @Test
    @Order(2)
    void adminSummaryAndHealth() throws Exception {
        AuthSession admin = adminSession();

        mockMvc.perform(get("/api/admin/summary").cookie(admin.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users_total").value(
                        org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.sessions_active").value(
                        org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        mockMvc.perform(get("/api/admin/health").cookie(admin.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overall").value("ok"))
                .andExpect(jsonPath("$.checks.length()").value(
                        org.hamcrest.Matchers.greaterThanOrEqualTo(3)));
    }

    @Test
    @Order(3)
    void metricsEndpointPrometheusText() throws Exception {
        AuthSession admin = adminSession();

        mockMvc.perform(post("/api/health")).andReturn();

        mockMvc.perform(get("/metrics"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "ops_agent_api_requests_total")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "process_uptime_seconds")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "ops_agent_audit_events_total")));
    }

    @Test
    @Order(4)
    void auditQueryExportForensicsComplianceArchive() throws Exception {
        AuthSession admin = adminSession();

        mockMvc.perform(get("/api/audit/query").cookie(admin.cookie())
                        .queryParam("user", "admin").queryParam("action", "login"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(
                        org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        mockMvc.perform(get("/api/audit/query").cookie(admin.cookie())
                        .queryParam("keyword", "init-admin"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/audit/export").cookie(admin.cookie())
                        .queryParam("event_type", "auth"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"eventType\":\"auth\"")));

        mockMvc.perform(get("/api/audit/forensics").cookie(admin.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.by_actor.length()").value(
                        org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.by_event_type.length()").value(
                        org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        mockMvc.perform(get("/api/audit/reports/compliance").cookie(admin.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.events").value(
                        org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.integrity.valid").value(true));

        mockMvc.perform(get("/api/audit/retention").cookie(admin.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").value(90));

        mockMvc.perform(post("/api/audit/archive").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(java.util.Map.of("days", 3650))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(0));
    }
}
