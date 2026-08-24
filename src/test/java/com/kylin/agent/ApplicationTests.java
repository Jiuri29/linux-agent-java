package com.kylin.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApplicationTests {

    private static final Path TMP = tempDir();

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("ops-agent.data-dir", () -> TMP.toString());
    }

    private static Path tempDir() {
        try {
            return Files.createTempDirectory("kylin-agent-test");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

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
    void bootstrapReportsUninitialized() throws Exception {
        mockMvc.perform(get("/api/auth/bootstrap"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.initialized").value(false));
    }

    @Test
    @Order(4)
    void initAdminLoginAndAuditFlow() throws Exception {
        mockMvc.perform(post("/api/auth/init-admin")
                        .contentType("application/json")
                        .content("{\"username\":\"admin\",\"password\":\"AdminStage17!\",\"displayName\":\"Administrator\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"admin\",\"password\":\"WrongPass1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthenticated"));

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"admin\",\"password\":\"AdminStage17!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.csrf_token").isNotEmpty())
                .andExpect(jsonPath("$.user.roles[0]").value("super_admin"))
                .andReturn();

        Cookie sessionCookie = null;
        for (Cookie cookie : login.getResponse().getCookies()) {
            if ("ops_session".equals(cookie.getName())) {
                sessionCookie = cookie;
            }
        }
        assertNotNull(sessionCookie);

        mockMvc.perform(post("/api/auth/logout").cookie(sessionCookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("csrf_required"));

        MvcResult relogin = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"admin\",\"password\":\"AdminStage17!\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie freshCookie = relogin.getResponse().getCookies()[0];

        mockMvc.perform(get("/api/v1/audit/recent").cookie(freshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        mockMvc.perform(get("/api/v1/audit/verify").cookie(freshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(true));
    }
}
