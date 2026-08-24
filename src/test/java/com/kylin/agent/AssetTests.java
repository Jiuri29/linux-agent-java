package com.kylin.agent;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AssetTests extends AbstractApiTest {

    private static String createdAssetId;

    @Test
    @Order(1)
    void createAssetWithValidation() throws Exception {
        AuthSession admin = adminSession();

        MvcResult createResult = mockMvc.perform(post("/api/assets").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.ofEntries(
                                Map.entry("hostname", "kylin-prod-01"),
                                Map.entry("ip", "10.0.0.11"),
                                Map.entry("os", "Kylin V11"),
                                Map.entry("arch", "loongarch64"),
                                Map.entry("environment", "production"),
                                Map.entry("tags", List.of("核心")),
                                Map.entry("groups", List.of("生产环境")),
                                Map.entry("applications", List.of("订单系统")),
                                Map.entry("ports", List.of(Map.of("port", 22, "service_name", "sshd"))))))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andReturn();

        createdAssetId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("id").asText();

        org.junit.jupiter.api.Assertions.assertNotNull(createdAssetId);
        org.junit.jupiter.api.Assertions.assertTrue(createdAssetId.startsWith("ast-"));

        mockMvc.perform(post("/api/assets").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of("hostname", "bad-ip", "ip", "999.1.1.1"))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/assets").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of("hostname", "dup", "ip", "10.0.0.11"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(2)
    void listDetailUpdateDelete() throws Exception {
        AuthSession admin = adminSession();

        mockMvc.perform(get("/api/assets").cookie(admin.cookie())
                        .queryParam("page", "1").queryParam("page_size", "10")
                        .queryParam("keyword", "kylin-prod"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.items[0].ports[0].service_name").value("sshd"));

        mockMvc.perform(get("/api/assets/detail").cookie(admin.cookie())
                        .queryParam("id", createdAssetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hostname").value("kylin-prod-01"));

        mockMvc.perform(post("/api/assets/update").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of("id", createdAssetId, "status", "maintenance"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("maintenance"));

        mockMvc.perform(post("/api/assets/delete").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of("id", createdAssetId))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/assets/detail").cookie(admin.cookie())
                        .queryParam("id", createdAssetId))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(3)
    void directoriesAndDependencies() throws Exception {
        AuthSession admin = adminSession();

        mockMvc.perform(post("/api/assets/groups").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of("name", "生产环境"))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/assets/groups").cookie(admin.cookie()))
                .andExpect(jsonPath("$[?(@ == '生产环境')]").isNotEmpty());

        mockMvc.perform(post("/api/assets/tags").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of("name", "核心"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/assets/applications").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of("name", "订单系统"))))
                .andExpect(status().isOk());

        String idA = createAsset(admin, "dep-a", "10.0.1.1");
        String idB = createAsset(admin, "dep-b", "10.0.1.2");

        mockMvc.perform(post("/api/assets/dependencies").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of("source_asset_id", idA, "target_asset_id", idB, "type", "depends_on"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/assets/dependencies").cookie(admin.cookie()))
                .andExpect(jsonPath("$[?(@.source_asset_id == '" + idA + "')]").isNotEmpty());

        deleteAsset(admin, idA);
        deleteAsset(admin, idB);
    }

    @Test
    @Order(4)
    void syncLocalStoresSnapshot() throws Exception {
        AuthSession admin = adminSession();

        mockMvc.perform(post("/api/assets/sync").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of("source", "local"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.synced").value(1))
                .andExpect(jsonPath("$.asset_id").isNotEmpty());

        String localAssetId = com.jayway.jsonpath.JsonPath.read(
                mockMvc.perform(post("/api/assets/sync").cookie(admin.cookie())
                                .header("X-CSRF-Token", admin.csrf())
                                .contentType("application/json")
                                .content(json(Map.of("source", "local"))))
                        .andReturn().getResponse().getContentAsString(), "$.asset_id");

        mockMvc.perform(get("/api/assets/snapshots").cookie(admin.cookie())
                        .queryParam("asset_id", localAssetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));

        mockMvc.perform(post("/api/assets/sync").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of("source", "manual"))))
                .andExpect(jsonPath("$.synced").value(0));

        mockMvc.perform(post("/api/assets/sync").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of("source", "bogus"))))
                .andExpect(status().isBadRequest());
    }

    private String createAsset(AuthSession admin, String hostname, String ip) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/assets").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of("hostname", hostname, "ip", ip))))
                .andExpect(status().isOk())
                .andReturn();
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private void deleteAsset(AuthSession admin, String id) throws Exception {
        mockMvc.perform(post("/api/assets/delete").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of("id", id))))
                .andExpect(status().isOk());
    }
}
