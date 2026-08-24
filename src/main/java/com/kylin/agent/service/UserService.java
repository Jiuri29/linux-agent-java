package com.kylin.agent.service;

import com.kylin.agent.audit.AuditService;
import com.kylin.agent.repository.RoleRepository;
import com.kylin.agent.repository.UserRepository;
import com.kylin.agent.security.AuthInterceptor;
import com.kylin.agent.security.PasswordHasher;
import com.kylin.agent.security.RbacCatalog;
import com.kylin.agent.security.RoleDefinition;
import com.kylin.agent.security.SessionStore;
import com.kylin.agent.security.Tokens;
import com.kylin.agent.security.UserAccount;
import com.kylin.agent.web.AppException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class UserService {

    private static final long LOCK_DURATION_MS = 15 * 60 * 1000L;

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordHasher hasher;
    private final SessionStore sessions;
    private final Tokens tokens;
    private final AuditService audit;

    public UserService(UserRepository users, RoleRepository roles, PasswordHasher hasher,
                       SessionStore sessions, Tokens tokens, AuditService audit) {
        this.users = users;
        this.roles = roles;
        this.hasher = hasher;
        this.sessions = sessions;
        this.tokens = tokens;
        this.audit = audit;
    }

    public List<Map<String, Object>> listUsers() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (UserAccount u : users.loadAll()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("username", u.getUsername());
            item.put("display_name", u.getDisplayName());
            item.put("roles", u.getRoles());
            item.put("enabled", u.isEnabled());
            item.put("locked", isLocked(u));
            item.put("created_at", u.getCreatedAt() == null ? null : u.getCreatedAt().toString());
            item.put("last_login_at", u.getLastLoginAt() == null ? null : u.getLastLoginAt().toString());
            result.add(item);
        }
        return result;
    }

    public UserAccount create(String username, String password, String displayName, String role) {
        if (username == null || !username.matches("^[a-zA-Z0-9_.-]{3,32}$")) {
            throw AppException.validation("username must match ^[a-zA-Z0-9_.-]{3,32}$");
        }
        if (users.findByUsername(username).isPresent()) {
            throw AppException.validation("username already exists");
        }
        validatePassword(password);
        List<String> resolvedRoles = resolveRoles(role);
        if (resolvedRoles.contains("super_admin")) {
            throw AppException.validation("cannot create another super_admin");
        }

        UserAccount account = new UserAccount();
        account.setUsername(username);
        account.setDisplayName(displayName);
        account.setPasswordHash(hasher.hash(password));
        account.setRoles(resolvedRoles);
        account.setEnabled(true);
        account.setCreatedAt(Instant.now());
        List<UserAccount> all = new ArrayList<>(users.loadAll());
        all.add(account);
        users.saveAll(all);
        audit.append("user", operator(), "create", "user:" + username, "success",
                Map.of("roles", resolvedRoles));
        return account;
    }

    public UserAccount update(String username, String displayName, List<String> newRoles) {
        UserAccount user = requireMutableUser(username);
        List<UserAccount> all = users.loadAll();
        if (displayName != null && !displayName.isBlank()) {
            user.setDisplayName(displayName);
        }
        if (newRoles != null) {
            for (String role : newRoles) {
                requireRoleExists(role);
            }
            if (newRoles.contains("super_admin") || user.getRoles().contains("super_admin")) {
                throw AppException.validation("super_admin role cannot be reassigned");
            }
            user.setRoles(new ArrayList<>(new LinkedHashSet<>(newRoles)));
        }
        users.saveAll(replace(all, user));
        audit.append("user", operator(), "update", "user:" + username, "success", Map.of());
        return user;
    }

    public UserAccount setStatus(String username, String status) {
        UserAccount user = requireMutableUser(username);
        List<UserAccount> all = users.loadAll();
        switch (status) {
            case "enabled" -> {
                user.setEnabled(true);
                user.setLockedUntilEpochMs(null);
                user.setFailedAttempts(0);
            }
            case "disabled" -> {
                user.setEnabled(false);
                sessions.invalidateForUser(username);
            }
            case "locked" -> {
                user.setLockedUntilEpochMs(Instant.now().toEpochMilli() + LOCK_DURATION_MS);
                user.setFailedAttempts(0);
                sessions.invalidateForUser(username);
            }
            default -> throw AppException.validation("status must be enabled, disabled or locked");
        }
        users.saveAll(replace(all, user));
        audit.append("user", operator(), "status:" + status, "user:" + username, "success", Map.of());
        return user;
    }

    public void resetPassword(String username, String newPassword) {
        UserAccount user = requireMutableUser(username);
        validatePassword(newPassword);
        user.setPasswordHash(hasher.hash(newPassword));
        user.setFailedAttempts(0);
        user.setLockedUntilEpochMs(null);
        users.saveAll(replace(users.loadAll(), user));
        sessions.invalidateForUser(username);
        audit.append("user", operator(), "reset-password", "user:" + username, "success", Map.of());
    }

    public int revokeSessions(String username) {
        requireUser(username);
        int removed = sessions.invalidateForUser(username);
        audit.append("user", operator(), "revoke-sessions", "user:" + username, "success",
                Map.of("revoked", removed));
        return removed;
    }

    public List<Map<String, Object>> listRoles() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (String builtin : RbacCatalog.BUILTIN_ROLES) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", builtin);
            item.put("permissions", RbacCatalog.ROLE_PERMISSIONS.get(builtin));
            item.put("builtin", true);
            result.add(item);
        }
        for (RoleDefinition custom : roles.loadAll()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", custom.name());
            item.put("permissions", custom.permissions());
            item.put("builtin", false);
            result.add(item);
        }
        return result;
    }

    public RoleDefinition upsertRole(String name, List<String> permissions) {
        if (name == null || !name.matches("^[a-zA-Z0-9_-]{2,32}$")) {
            throw AppException.validation("role name must match ^[a-zA-Z0-9_-]{2,32}$");
        }
        if (RbacCatalog.isBuiltinRole(name)) {
            throw AppException.validation("builtin role cannot be modified");
        }
        Set<String> clean = new LinkedHashSet<>();
        if (permissions != null) {
            for (String p : permissions) {
                if (p == null || p.isBlank()) {
                    continue;
                }
                if (!p.equals(RbacCatalog.WILDCARD) && !p.matches("^[a-z]+:[a-z_:\\*]+$")) {
                    throw AppException.validation("invalid permission: " + p);
                }
                clean.add(p.trim());
            }
        }
        RoleDefinition definition = new RoleDefinition(name, new ArrayList<>(clean));
        List<RoleDefinition> all = new ArrayList<>(roles.loadAll().stream()
                .filter(r -> !r.name().equalsIgnoreCase(name))
                .toList());
        all.add(definition);
        roles.saveAll(all);
        audit.append("role", operator(), "upsert", "role:" + name, "success",
                Map.of("permissions", clean));
        return definition;
    }

    public void deleteRole(String name) {
        if (RbacCatalog.isBuiltinRole(name)) {
            throw AppException.validation("builtin role cannot be deleted");
        }
        RoleDefinition existing = roles.findByName(name)
                .orElseThrow(() -> AppException.notFound("role not found"));
        List<RoleDefinition> remaining = new ArrayList<>(roles.loadAll());
        remaining.remove(existing);
        roles.saveAll(remaining);

        List<UserAccount> all = users.loadAll();
        boolean changed = false;
        for (UserAccount u : all) {
            if (u.getRoles().removeIf(r -> r.equalsIgnoreCase(name))) {
                changed = true;
            }
        }
        if (changed) {
            users.saveAll(all);
        }
        audit.append("role", operator(), "delete", "role:" + name, "success", Map.of());
    }

    public void validatePassword(String password) {
        if (password == null || password.length() < 8) {
            throw AppException.validation("password must be at least 8 characters");
        }
        boolean hasUpper = password.chars().anyMatch(Character::isUpperCase);
        boolean hasLower = password.chars().anyMatch(Character::isLowerCase);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasUpper || !hasLower || !hasDigit) {
            throw AppException.validation("password must contain upper case, lower case and digit");
        }
    }

    public List<String> resolveRoles(String role) {
        if (role == null || role.isBlank()) {
            return List.of("operator");
        }
        requireRoleExists(role);
        return List.of(role);
    }

    public void requireRoleExists(String role) {
        if (RbacCatalog.isBuiltinRole(role) || roles.findByName(role).isPresent()) {
            return;
        }
        throw AppException.validation("unknown role: " + role);
    }

    public boolean isLocked(UserAccount user) {
        return user.getLockedUntilEpochMs() != null
                && Instant.now().toEpochMilli() < user.getLockedUntilEpochMs();
    }

    private UserAccount requireUser(String username) {
        return users.findByUsername(username)
                .orElseThrow(() -> AppException.notFound("user not found"));
    }

    private UserAccount requireMutableUser(String username) {
        UserAccount user = requireUser(username);
        if (user.getRoles().contains("super_admin")) {
            throw AppException.validation("super_admin account cannot be modified here");
        }
        return user;
    }

    private List<UserAccount> replace(List<UserAccount> all, UserAccount updated) {
        List<UserAccount> result = new ArrayList<>(all.size());
        for (UserAccount u : all) {
            result.add(u.getUsername().equals(updated.getUsername()) ? updated : u);
        }
        return result;
    }

    private String operator() {
        var attributes = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (attributes instanceof org.springframework.web.context.request.ServletRequestAttributes servlet) {
            Object operator = servlet.getRequest().getAttribute(AuthInterceptor.OPERATOR_ATTRIBUTE);
            if (operator != null) {
                return operator.toString();
            }
        }
        return "system";
    }
}
