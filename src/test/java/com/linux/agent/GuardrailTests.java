package com.linux.agent;

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
class GuardrailTests extends AbstractApiTest {

    @Test
    @Order(1)
    void defaultRulesSeededAndListed() throws Exception {
        AuthSession admin = adminSession();

        mockMvc.perform(get("/api/guardrails/rules").cookie(admin.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == 'gd-deny-catastrophic')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.id == 'gd-approve-rm')]").isNotEmpty());
    }

    @Test
    @Order(2)
    void evaluateBlocksApprovesAllows() throws Exception {
        AuthSession admin = adminSession();

        mockMvc.perform(post("/api/guardrails/evaluate").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of(
                                "intent", "清理磁盘空间",
                                "commands", List.of(
                                        "dd if=/dev/zero of=/dev/sda",
                                        "rm -rf /tmp/cache",
                                        "df -kP")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overall").value("blocked"))
                .andExpect(jsonPath("$.decisions[0].decision").value("blocked"))
                .andExpect(jsonPath("$.decisions[1].decision").value("approval_required"))
                .andExpect(jsonPath("$.decisions[2].decision").value("allowed"));

        mockMvc.perform(get("/api/guardrails/approvals").cookie(admin.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(
                        org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    @Order(3)
    void customRuleSaveTestToggle() throws Exception {
        AuthSession admin = adminSession();

        MvcResultHolder saved = new MvcResultHolder();
        mockMvc.perform(post("/api/guardrails/rules").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of(
                                "name", "禁止chmod777",
                                "command_pattern", "^\\s*chmod\\s+777\\b",
                                "action", "deny",
                                "severity", "critical"))))
                .andExpect(status().isOk())
                .andDo(result -> saved.result = result);
        String ruleId = objectMapper.readTree(saved.result.getResponse().getContentAsString())
                .path("id").asText();

        mockMvc.perform(post("/api/guardrails/rules/test").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of("command_pattern", "^\\s*chmod\\s+777\\b",
                                "action", "deny",
                                "command", "chmod 777 /etc/passwd"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matched").value(true))
                .andExpect(jsonPath("$.action").value("deny"));

        mockMvc.perform(post("/api/guardrails/evaluate").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of("intent", "改权限",
                                "commands", List.of("chmod 777 /tmp/x")))))
                .andExpect(jsonPath("$.overall").value("blocked"));

        mockMvc.perform(post("/api/guardrails/rules/status").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of("id", ruleId, "enabled", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        mockMvc.perform(post("/api/guardrails/evaluate").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of("intent", "改权限",
                                "commands", List.of("chmod 777 /tmp/x")))))
                .andExpect(jsonPath("$.overall").value("allowed"));
    }

    @Test
    @Order(4)
    void exemptionBypassesApproval() throws Exception {
        AuthSession admin = adminSession();

        mockMvc.perform(post("/api/guardrails/evaluate").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of("intent", "重启服务",
                                "commands", List.of("systemctl restart sshd")))))
                .andExpect(jsonPath("$.decisions[0].decision").value("approval_required"));

        mockMvc.perform(post("/api/guardrails/exemptions").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of(
                                "pattern", "^systemctl\\s+restart",
                                "reason", "测试豁免"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/guardrails/exemptions").cookie(admin.cookie()))
                .andExpect(jsonPath("$[?(@.reason == '测试豁免')]").isNotEmpty());

        mockMvc.perform(post("/api/guardrails/evaluate").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of("intent", "重启服务",
                                "commands", List.of("systemctl restart sshd")))))
                .andExpect(jsonPath("$.overall").value("allowed"))
                .andExpect(jsonPath("$.decisions[0].exempt_by").isNotEmpty());
    }

    private static class MvcResultHolder {
        org.springframework.test.web.servlet.MvcResult result;
    }
}
