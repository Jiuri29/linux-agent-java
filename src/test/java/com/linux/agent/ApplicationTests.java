package com.linux.agent;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApplicationTests extends AbstractApiTest {

    @Test
    void contextLoads() {
    }

    @Test
    @Order(1)
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ok"));
    }

    @Test
    @Order(2)
    void protectedEndpointRequiresSession() throws Exception {
        mockMvc.perform(get("/api/audit/recent"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthenticated"));
    }

    @Test
    @Order(3)
    void bootstrapReportsInitializedFlag() throws Exception {
        mockMvc.perform(get("/api/auth/bootstrap"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.initialized").isBoolean());
    }

    @Test
    @Order(4)
    void loginCsrfAndAuditFlow() throws Exception {
        AuthSession session = adminSession();
        assertNotNull(session.cookie());

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"admin\",\"password\":\"WrongPass1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthenticated"));

        mockMvc.perform(post("/api/auth/logout").cookie(session.cookie()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("csrf_required"));

        AuthSession refreshed = adminSession();

        mockMvc.perform(get("/api/v1/audit/recent").cookie(refreshed.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        mockMvc.perform(get("/api/v1/audit/verify").cookie(refreshed.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(true));
    }
}
