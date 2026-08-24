package com.kylin.agent;

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
class TerminalTests extends AbstractApiTest {

    private static String runId;

    @Test
    @Order(1)
    void allowlistedCommandRuns() throws Exception {
        AuthSession admin = adminSession();

        mockMvc.perform(post("/api/terminal/run").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of("command", "hostname"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.exit_code").value(0))
                .andDo(result -> runId = objectMapper.readTree(
                                result.getResponse().getContentAsString())
                        .path("id").asText());

        org.junit.jupiter.api.Assertions.assertNotNull(runId);

        mockMvc.perform(get("/api/terminal/history").cookie(admin.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.command == 'hostname')]").isNotEmpty());

        mockMvc.perform(get("/api/terminal/replay").cookie(admin.cookie())
                        .queryParam("id", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.output").isNotEmpty());
    }

    @Test
    @Order(2)
    void blockedCommandsRejected() throws Exception {
        AuthSession admin = adminSession();

        for (String command : new String[]{"rm -rf /", "cat /etc/passwd",
                "ls; echo hacked", "sudo reboot"}) {
            mockMvc.perform(post("/api/terminal/run").cookie(admin.cookie())
                            .header("X-CSRF-Token", admin.csrf())
                            .contentType("application/json")
                            .content(json(Map.of("command", command))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.data.error").value("command_not_allowed"));
        }
    }

    @Test
    @Order(3)
    void templateSaveRenderAndRun() throws Exception {
        AuthSession admin = adminSession();

        MvcResult saved = mockMvc.perform(post("/api/terminal/templates").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of(
                                "name", "disk-usage",
                                "command_template", "df {{opts}}",
                                "description", "查看磁盘"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andReturn();
        String templateId = objectMapper.readTree(saved.getResponse().getContentAsString())
                .path("id").asText();

        mockMvc.perform(get("/api/terminal/templates").cookie(admin.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(
                        org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$[0].id").value(templateId));

        mockMvc.perform(post("/api/terminal/templates/render").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of("template_id", templateId,
                                "params", Map.of("opts", "-kP")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.command").value("df -kP"));
    }
}
