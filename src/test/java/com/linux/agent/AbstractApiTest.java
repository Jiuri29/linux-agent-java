package com.linux.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractApiTest {

    protected static final Path TMP = tempDir();

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    private static boolean adminReady;
    private static Cookie adminCookie;
    private static String adminCsrfToken;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("ops-agent.data-dir", () -> TMP.toString());
    }

    @BeforeEach
    void ensureAdminInitialized() throws Exception {
        if (adminReady) {
            return;
        }
        mockMvc.perform(post("/api/auth/init-admin")
                        .contentType("application/json")
                        .content("{\"username\":\"admin\",\"password\":\"AdminStage17!\",\"displayName\":\"Administrator\"}"))
                .andReturn();
        adminReady = true;
    }

    protected synchronized AuthSession adminSession() throws Exception {
        ensureAdminInitialized();
        if (adminCookie != null) {
            MvcResult probe = mockMvc.perform(get("/api/auth/bootstrap")).andReturn();
            if (probe.getResponse().getStatus() == 200 && adminStillValid()) {
                return new AuthSession(adminCookie, adminCsrfToken);
            }
        }
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"admin\",\"password\":\"AdminStage17!\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andReturn();
        adminCookie = login.getResponse().getCookies()[0];
        adminCsrfToken = objectMapper.readTree(login.getResponse().getContentAsString())
                .path("csrf_token").asText();
        return new AuthSession(adminCookie, adminCsrfToken);
    }

    private boolean adminStillValid() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/audit/recent").cookie(adminCookie)).andReturn();
        return result.getResponse().getStatus() != 401;
    }

    protected String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public record AuthSession(Cookie cookie, String csrf) {
    }

    private static Path tempDir() {
        try {
            return Files.createTempDirectory("Linux-agent-test");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
