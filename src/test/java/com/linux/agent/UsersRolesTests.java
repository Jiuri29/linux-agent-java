package com.linux.agent;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.context.SpringBootTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UsersRolesTests extends AbstractApiTest {

    @Test
    @Order(1)
    void createListAndUpdateUser() throws Exception {
        AuthSession admin = adminSession();

        mockMvc.perform(post("/api/users").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(java.util.Map.of(
                                "username", "operator1",
                                "password", "OperatorStage17!",
                                "display_name", "值班运维",
                                "role", "operator"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));

        mockMvc.perform(post("/api/users").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(java.util.Map.of(
                                "username", "operator1",
                                "password", "OperatorStage17!"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));

        mockMvc.perform(get("/api/users").cookie(admin.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.username == 'operator1')]").isNotEmpty());

        mockMvc.perform(post("/api/users/update").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(java.util.Map.of(
                                "username", "operator1",
                                "display_name", "白班运维"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/users").cookie(admin.cookie()))
                .andExpect(jsonPath("$[?(@.username == 'operator1' && @.display_name == '白班运维')]").isNotEmpty());
    }

    @Test
    @Order(2)
    void disabledUserCannotLogin() throws Exception {
        AuthSession admin = adminSession();

        mockMvc.perform(post("/api/users").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(java.util.Map.of(
                                "username", "op2",
                                "password", "Operator2Pass!", "role", "viewer"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/users/status").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(java.util.Map.of("username", "op2", "status", "disabled"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"op2\",\"password\":\"Operator2Pass!\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthenticated"));
    }

    @Test
    @Order(3)
    void rolesDirectoryAndCustomRoles() throws Exception {
        AuthSession admin = adminSession();

        mockMvc.perform(get("/api/roles").cookie(admin.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'super_admin')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.name == 'operator')]").isNotEmpty());

        mockMvc.perform(post("/api/roles/update").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(java.util.Map.of(
                                "name", "release_mgr",
                                "permissions", java.util.List.of("asset:read", "monitor:read"))))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("release_mgr"));

        mockMvc.perform(get("/api/roles").cookie(admin.cookie()))
                .andExpect(jsonPath("$[?(@.name == 'release_mgr')]").isNotEmpty());

        mockMvc.perform(post("/api/roles/delete").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(java.util.Map.of("name", "release_mgr"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/roles/delete").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(java.util.Map.of("name", "super_admin"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(4)
    void resetPasswordFlow() throws Exception {
        AuthSession admin = adminSession();
        mockMvc.perform(post("/api/users").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(java.util.Map.of(
                                "username", "op3",
                                "password", "Operator3Pass!"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/users/reset-password").cookie(admin.cookie())
                        .header("X-CSRF-Token", admin.csrf())
                        .contentType("application/json")
                        .content(json(java.util.Map.of("username", "op3", "password", "NewPass1234"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"op3\",\"password\":\"NewPass1234\"}"))
                .andExpect(status().isOk());
    }
}
