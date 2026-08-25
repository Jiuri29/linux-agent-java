package com.linux.agent;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ChatopsTests extends AbstractApiTest {

    private static HttpServer fakeAgent;
    private static int fakeAgentPort;

    @AfterAll
    static void stopFakeAgent() {
        if (fakeAgent != null) {
            fakeAgent.stop(0);
        }
    }

    private static void startFakeAgent(String reply) throws Exception {
        fakeAgent = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        fakeAgentPort = fakeAgent.getAddress().getPort();
        fakeAgent.createContext("/", exchange -> {
            byte[] body = ("{\"reply\":\"" + reply + "\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        fakeAgent.start();
    }

    @Test
    @Order(1)
    void templatesAndDemoCleanupWorkWithoutConfig() throws Exception {
        AuthSession admin = adminSession();

        mockMvc.perform(get("/api/chatops/templates").cookie(admin.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(
                        org.hamcrest.Matchers.greaterThanOrEqualTo(3)));

        mockMvc.perform(post("/api/chatops/demo-cleanup").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluation.decisions[0].command")
                        .value("journalctl --vacuum-time=7d"))
                .andExpect(jsonPath("$.evaluation.decisions[1].decision")
                        .value("approval_required"));
    }

    @Test
    @Order(2)
    void messageRequiresAgentConfig() throws Exception {
        AuthSession admin = adminSession();

        mockMvc.perform(post("/api/chatops/message").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of("message", "hello"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_agent_api_config"));
    }

    @Test
    @Order(3)
    void messageWithSafeReplyFlow() throws Exception {
        AuthSession admin = adminSession();
        startFakeAgent("建议执行巡检命令：\\n$ df -kP\\n$ free -m");

        mockMvc.perform(post("/api/config/agent-api").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of(
                                "base_url", "http://127.0.0.1:" + fakeAgentPort,
                                "enabled", true,
                                "allowed_hosts", List.of("127.0.0.1")))))
                .andExpect(status().isOk());

        MvcResultHolder sent = new MvcResultHolder();
        mockMvc.perform(post("/api/chatops/message").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of("message", "帮我巡检一下"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requires_confirm").value(false))
                .andExpect(jsonPath("$.candidates.length()").value(2))
                .andDo(result -> sent.result = result);
        String sessionId = objectMapper.readTree(sent.result.getResponse().getContentAsString())
                .path("session_id").asText();

        mockMvc.perform(get("/api/chatops/session").cookie(admin.cookie())
                        .queryParam("id", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(
                        org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
    }

    @Test
    @Order(4)
    void riskyReplyRequiresConfirmThenConfirmed() throws Exception {
        AuthSession admin = adminSession();
        startFakeAgent("可以清理日志：\\n$ journalctl --vacuum-time=3d");

        mockMvc.perform(post("/api/config/agent-api").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of(
                                "base_url", "http://127.0.0.1:" + fakeAgentPort,
                                "enabled", true,
                                "allowed_hosts", List.of("127.0.0.1")))))
                .andExpect(status().isOk());

        MvcResultHolder sent = new MvcResultHolder();
        mockMvc.perform(post("/api/chatops/message").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of("message", "清理系统日志"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requires_confirm").value(true))
                .andExpect(jsonPath("$.action_id").isNotEmpty())
                .andDo(result -> sent.result = result);

        String body = sent.result.getResponse().getContentAsString();
        String sessionId = objectMapper.readTree(body).path("session_id").asText();
        String actionId = objectMapper.readTree(body).path("action_id").asText();

        mockMvc.perform(post("/api/chatops/confirm").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of("session_id", sessionId, "action_id", actionId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));

        mockMvc.perform(post("/api/chatops/confirm").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of("session_id", sessionId, "action_id", actionId))))
                .andExpect(status().isBadRequest());
    }

    private static class MvcResultHolder {
        org.springframework.test.web.servlet.MvcResult result;
    }
}
