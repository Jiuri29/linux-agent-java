package com.kylin.agent;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FileTests extends AbstractApiTest {

    private static Path sandbox;

    @Test
    @Order(1)
    void listDirectoryInsideRoot() throws Exception {
        AuthSession admin = adminSession();
        sandbox = Path.of(TMP.toString(), "files");
        Files.createDirectories(sandbox);
        Files.writeString(sandbox.resolve("a.txt"), "hello");
        Files.createDirectories(sandbox.resolve("sub"));

        mockMvc.perform(get("/api/files/list").cookie(admin.cookie())
                        .queryParam("path", sandbox.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'a.txt' && @.type == 'file')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.name == 'sub' && @.type == 'dir')]").isNotEmpty());
    }

    @Test
    @Order(2)
    void pathOutsideRootsRejected() throws Exception {
        AuthSession admin = adminSession();
        String outside = Path.of(TMP.toString()).resolve("outside.txt").toString();

        mockMvc.perform(get("/api/files/read").cookie(admin.cookie())
                        .queryParam("path", outside))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.data.error").value("path_not_allowed"));

        mockMvc.perform(post("/api/files/write").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of("path", outside, "content", "nope"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(3)
    void writeReadDiffRestoreFlow() throws Exception {
        AuthSession admin = adminSession();
        String target = sandbox.resolve("cfg.txt").toString();

        mockMvc.perform(post("/api/files/write").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of("path", target, "content", "v1"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/files/read").cookie(admin.cookie())
                        .queryParam("path", target))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("v1"));

        mockMvc.perform(post("/api/files/write").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of("path", target, "content", "v2"))))
                .andExpect(status().isOk());

        MvcResultHolder backups = new MvcResultHolder();
        mockMvc.perform(get("/api/files/backups").cookie(admin.cookie())
                        .queryParam("path", target))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andDo(result -> backups.result = result);
        String backupId = com.jayway.jsonpath.JsonPath.read(
                backups.result.getResponse().getContentAsString(), "$[0].backup_id");

        mockMvc.perform(get("/api/files/diff").cookie(admin.cookie())
                        .queryParam("path", target).queryParam("backup_id", backupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added").value(1))
                .andExpect(jsonPath("$.removed").value(1));

        mockMvc.perform(post("/api/files/restore").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of("backup_id", backupId))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/files/read").cookie(admin.cookie())
                        .queryParam("path", target))
                .andExpect(jsonPath("$.content").value("v1"));
    }

    @Test
    @Order(4)
    void uploadDeleteAndPrepare() throws Exception {
        AuthSession admin = adminSession();

        byte[] payload = "uploaded-content".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        org.springframework.mock.web.MockMultipartFile multipart =
                new org.springframework.mock.web.MockMultipartFile(
                        "file", "upload.bin", "application/octet-stream", payload);

        mockMvc.perform(multipart("/api/files/upload").file(multipart)
                        .param("path", sandbox.resolve("upload.bin").toString())
                        .cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size_bytes").value(payload.length));

        mockMvc.perform(post("/api/files/delete").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of("path", sandbox.resolve("upload.bin").toString()))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/files/backups").cookie(admin.cookie())
                        .queryParam("path", sandbox.resolve("upload.bin").toString()))
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(post("/api/files/operation/prepare").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(Map.of("action", "write",
                                "path", sandbox.resolve("plan.txt").toString(),
                                "content", "x"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("planned"))
                .andExpect(jsonPath("$.plan_id").isNotEmpty());
    }

    private static class MvcResultHolder {
        org.springframework.test.web.servlet.MvcResult result;
    }
}
