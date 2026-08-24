package com.kylin.agent;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ServiceCenterTests extends AbstractApiTest {

    private static String serviceId;
    private static String versionOneId;
    private static String versionTwoId;
    private static Path configFile;

    @Test
    @Order(1)
    void createServiceAndDetail() throws Exception {
        AuthSession admin = adminSession();
        try {
            configFile = Files.createTempFile(TMP, "ops-agent", ".conf");
            Files.writeString(configFile, "a=1\nb=2\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        MvcResult result = mockMvc.perform(post("/api/services").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of(
                                "name", "ops-agent",
                                "unit", "ops-agent.service",
                                "config_path", configFile.toString(),
                                "description", "local agent"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andReturn();
        serviceId = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("id").asText();

        mockMvc.perform(get("/api/services/detail").cookie(admin.cookie())
                        .queryParam("id", serviceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("ops-agent"));

        mockMvc.perform(get("/api/services").cookie(admin.cookie()))
                .andExpect(jsonPath("$[?(@.id == '" + serviceId + "')]").isNotEmpty());
    }

    @Test
    @Order(2)
    void saveVersionReadAndDrift() throws Exception {
        AuthSession admin = adminSession();

        MvcResult saved = mockMvc.perform(post("/api/services/config/versions")
                        .cookie(admin.cookie()).header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of("service_id", serviceId,
                                "content", "a=1\nb=2\n", "comment", "baseline"))))
                .andExpect(status().isOk())
                .andReturn();
        versionOneId = objectMapper.readTree(saved.getResponse().getContentAsString())
                .path("id").asText();

        mockMvc.perform(get("/api/services/config/versions").cookie(admin.cookie())
                        .queryParam("service_id", serviceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get("/api/services/config/versions/detail").cookie(admin.cookie())
                        .queryParam("id", versionOneId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("a=1\nb=2\n"));

        mockMvc.perform(get("/api/services/config/drift").cookie(admin.cookie())
                        .queryParam("service_id", serviceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.drifted").value(false));

        Files.writeString(configFile, "a=9\nb=2\n");

        mockMvc.perform(get("/api/services/config/drift").cookie(admin.cookie())
                        .queryParam("service_id", serviceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.drifted").value(true))
                .andExpect(jsonPath("$.diff").isNotEmpty());

        mockMvc.perform(get("/api/services/config/read").cookie(admin.cookie())
                        .queryParam("service_id", serviceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("a=9\nb=2\n"));
    }

    @Test
    @Order(3)
    void diffBetweenVersions() throws Exception {
        AuthSession admin = adminSession();

        MvcResult saved = mockMvc.perform(post("/api/services/config/versions")
                        .cookie(admin.cookie()).header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of("service_id", serviceId,
                                "content", "a=2\nb=2\nc=3\n", "comment", "tune a, add c"))))
                .andExpect(status().isOk())
                .andReturn();
        versionTwoId = objectMapper.readTree(saved.getResponse().getContentAsString())
                .path("id").asText();

        mockMvc.perform(get("/api/services/config/diff").cookie(admin.cookie())
                        .queryParam("from", versionOneId).queryParam("to", versionTwoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added").value(2))
                .andExpect(jsonPath("$.removed").value(1));

        mockMvc.perform(get("/api/services/config/diff").cookie(admin.cookie())
                        .queryParam("from", versionOneId).queryParam("to", "disk"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(4)
    void lifecycleRecordsChange() throws Exception {
        AuthSession admin = adminSession();

        mockMvc.perform(post("/api/services/lifecycle").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of("service_id", serviceId, "action", "restart"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("restart"))
                .andExpect(jsonPath("$.status").value("ok"));

        mockMvc.perform(get("/api/services/changes").cookie(admin.cookie())
                        .queryParam("service_id", serviceId).queryParam("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.type == 'lifecycle')]").isNotEmpty());
    }

    @Test
    @Order(5)
    void rollbackRestoresDiskContent() throws Exception {
        AuthSession admin = adminSession();

        mockMvc.perform(post("/api/services/config/rollback")
                        .cookie(admin.cookie()).header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of("service_id", serviceId,
                                "version_id", versionOneId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rolled_back_to").value(versionOneId));

        try {
            assertEquals("a=1\nb=2\n", Files.readString(configFile));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        mockMvc.perform(get("/api/services/config/drift").cookie(admin.cookie())
                        .queryParam("service_id", serviceId)
                        .queryParam("version_id", versionOneId))
                .andExpect(jsonPath("$.drifted").value(false));
    }
}
