package com.kylin.agent;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AgentApiConfigTests extends AbstractApiTest {

    @Test
    @Order(1)
    void unconfiguredByDefault() throws Exception {
        AuthSession admin = adminSession();

        mockMvc.perform(get("/api/config/agent-api").cookie(admin.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false));
    }

    @Test
    @Order(2)
    void saveRejectsDisallowedHostAndBadUrl() throws Exception {
        AuthSession admin = adminSession();

        mockMvc.perform(post("/api/config/agent-api").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of("base_url", "ftp://example.com"))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/config/agent-api").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of(
                                "base_url", "http://evil.example.com/api",
                                "allowed_hosts", List.of("127.0.0.1", "localhost")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_agent_api_config"));
    }

    @Test
    @Order(3)
    void saveMasksKeyAndPersists() throws Exception {
        AuthSession admin = adminSession();

        mockMvc.perform(post("/api/config/agent-api").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of(
                                "base_url", "http://127.0.0.1:18090",
                                "api_key", "secret-key-123",
                                "model", "demo-model",
                                "enabled", true,
                                "allowed_hosts", List.of("127.0.0.1", "localhost")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.base_url").value("http://127.0.0.1:18090"))
                .andExpect(jsonPath("$.api_key").value("******"));

        mockMvc.perform(get("/api/config/agent-api").cookie(admin.cookie()))
                .andExpect(jsonPath("$.api_key").value("******"));
    }

    @Test
    @Order(4)
    void connectivityTestReportsUnreachable() throws Exception {
        AuthSession admin = adminSession();

        mockMvc.perform(post("/api/config/agent-api/test").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_agent_api_config"));
    }
}
