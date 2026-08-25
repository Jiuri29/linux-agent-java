package com.kylin.agent;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class McpTests extends AbstractApiTest {

    @Test
    @Order(1)
    void builtinToolsSeeded() throws Exception {
        AuthSession admin = adminSession();

        mockMvc.perform(get("/api/mcp/tools").cookie(admin.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'os.read_only_snapshot')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.name == 'guardrails.evaluate')]").isNotEmpty());

        mockMvc.perform(get("/api/mcp/capabilities").cookie(admin.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.server_info.name").value("kylin-aiops-agent"))
                .andExpect(jsonPath("$.tools_count").value(7));
    }

    @Test
    @Order(2)
    void invokeExecutableTools() throws Exception {
        AuthSession admin = adminSession();

        mockMvc.perform(post("/api/mcp/invoke").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of("name", "os.read_only_snapshot"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hostname").isNotEmpty());

        mockMvc.perform(post("/api/mcp/invoke").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of(
                                "name", "guardrails.evaluate",
                                "params", Map.of("intent", "检查",
                                        "commands", List.of("df -kP"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overall").value("allowed"));

        mockMvc.perform(post("/api/mcp/invoke").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of("name", "no.such.tool"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(3)
    void jsonRpcFacade() throws Exception {
        AuthSession admin = adminSession();

        mockMvc.perform(post("/api/mcp/rpc").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.server_info.name").value("kylin-aiops-agent"));

        mockMvc.perform(post("/api/mcp/rpc").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of("jsonrpc", "2.0", "id", 2, "method", "tools/call",
                                "params", Map.of("name", "monitor.latest")))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/mcp/rpc").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of("jsonrpc", "2.0", "id", 3,
                                "method", "bogus/method"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error.code").value(-32601));
    }

    @Test
    @Order(4)
    void pluginLifecycle() throws Exception {
        AuthSession admin = adminSession();

        mockMvc.perform(post("/api/mcp/plugins/register").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of("name", "demo-plugin",
                                "description", "演示插件",
                                "tools", List.of(Map.of(
                                        "name", "demo.hello",
                                        "description", "演示工具"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provided_tools[0]").value("demo.hello"));

        mockMvc.perform(post("/api/mcp/invoke").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of("name", "demo.hello"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("not_executable"));

        mockMvc.perform(get("/api/mcp/plugins").cookie(admin.cookie()))
                .andExpect(jsonPath("$[?(@.name == 'core')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.name == 'demo-plugin')]").isNotEmpty());

        mockMvc.perform(post("/api/mcp/plugins/uninstall").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of("name", "demo-plugin"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/mcp/plugins/uninstall").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of("name", "core"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(5)
    void toolStatusToggleBlocksInvocation() throws Exception {
        AuthSession admin = adminSession();

        mockMvc.perform(post("/api/mcp/tools/status").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of("name", "services.list", "enabled", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        mockMvc.perform(post("/api/mcp/invoke").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of("name", "services.list"))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/mcp/tools/status").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of("name", "services.list", "enabled", true))))
                .andExpect(status().isOk());
    }
}
