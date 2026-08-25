package com.linux.agent;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AcceptanceTests extends AbstractApiTest {

    private static String runId;

    @Test
    @Order(1)
    void catalogListsChecks() throws Exception {
        AuthSession admin = adminSession();

        mockMvc.perform(get("/api/tests/catalog").cookie(admin.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(
                        org.hamcrest.Matchers.greaterThanOrEqualTo(8)))
                .andExpect(jsonPath("$[?(@.id == 'audit.hashchain')]").isNotEmpty());
    }

    @Test
    @Order(2)
    void runAllChecksAllPassed() throws Exception {
        AuthSession admin = adminSession();

        MvcResultHolder holder = new MvcResultHolder();
        mockMvc.perform(post("/api/tests/run").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failed").value(0))
                .andExpect(jsonPath("$.total").value(
                        org.hamcrest.Matchers.greaterThanOrEqualTo(8)))
                .andExpect(jsonPath("$.run_id").isNotEmpty())
                .andDo(result -> holder.result = result);

        runId = objectMapper.readTree(holder.result.getResponse().getContentAsString())
                .path("run_id").asText();
        assertEquals(true, runId.startsWith("run-"));
    }

    @Test
    @Order(3)
    void runsHistoryDetailAndMarkdownReport() throws Exception {
        AuthSession admin = adminSession();

        mockMvc.perform(get("/api/tests/runs").cookie(admin.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(
                        org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        mockMvc.perform(get("/api/tests/runs/detail").cookie(admin.cookie())
                        .queryParam("id", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(
                        org.hamcrest.Matchers.greaterThanOrEqualTo(8)));

        mockMvc.perform(get("/api/tests/runs/report").cookie(admin.cookie())
                        .queryParam("id", runId))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("验收测试报告")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("| 用例 ID |")));

        mockMvc.perform(get("/api/tests/runs/detail").cookie(admin.cookie())
                        .queryParam("id", "run-missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(4)
    void agentRunRecordsTrigger() throws Exception {
        AuthSession admin = adminSession();

        mockMvc.perform(post("/api/tests/agent-run").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of("ids", java.util.List.of("app.health", "audit.hashchain")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trigger").value("agent"))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.failed").value(0));
    }

    private static class MvcResultHolder {
        org.springframework.test.web.servlet.MvcResult result;
    }
}
