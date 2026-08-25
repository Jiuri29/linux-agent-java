package com.linux.agent.service;

import com.linux.agent.audit.AuditService;
import com.linux.agent.repository.UserRepository;
import com.linux.agent.security.PasswordHasher;
import com.linux.agent.security.RbacCatalog;
import com.linux.agent.security.SessionStore;
import com.linux.agent.security.Tokens;
import com.linux.agent.security.UserAccount;
import com.linux.agent.web.AppException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class AuthService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCK_DURATION_MS = 15 * 60 * 1000L;

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final SessionStore sessions;
    private final Tokens tokens;
    private final AuditService audit;

    public AuthService(UserRepository users, PasswordHasher hasher, SessionStore sessions,
                       Tokens tokens, AuditService audit) {
        this.users = users;
        this.hasher = hasher;
        this.sessions = sessions;
        this.tokens = tokens;
        this.audit = audit;
    }

    public boolean initialized() {
        return !users.isEmpty();
    }

    public UserAccount initAdmin(String username, String password, String displayName) {
        if (initialized()) {
            throw AppException.validation("admin already initialized");
        }
        validatePassword(password);
        UserAccount account = new UserAccount();
        account.setUsername(username);
        account.setDisplayName(displayName);
        account.setPasswordHash(hasher.hash(password));
        account.setRoles(List.of("super_admin"));
        account.setEnabled(true);
        account.setCreatedAt(Instant.now());
        users.saveAll(new java.util.ArrayList<>(List.of(account)));
        audit.append("auth", "system", "init-admin", "user:" + username, "success",
                Map.of("roles", account.getRoles()));
        return account;
    }

    public LoginResult login(String username, String password) {
        UserAccount user = users.findByUsername(username).orElse(null);
        Instant now = Instant.now();

        if (user != null && user.getLockedUntilEpochMs() != null
                && now.toEpochMilli() < user.getLockedUntilEpochMs()) {
            audit.append("auth", username, "login", "user:" + username, "locked", Map.of());
            throw AppException.unauthenticated("account locked");
        }

        if (user != null && !user.isEnabled()) {
            audit.append("auth", username, "login", "user:" + username, "disabled", Map.of());
            throw AppException.unauthenticated("account disabled");
        }

        if (user == null || !hasher.verify(password, user.getPasswordHash())) {
            if (user != null) {
                int attempts = user.getFailedAttempts() + 1;
                user.setFailedAttempts(attempts);
                if (attempts >= MAX_FAILED_ATTEMPTS) {
                    user.setLockedUntilEpochMs(now.toEpochMilli() + LOCK_DURATION_MS);
                    user.setFailedAttempts(0);
                }
                users.saveAll(replaceUser(users.loadAll(), user));
            }
            audit.append("auth", username, "login", "user:" + username, "failure", Map.of());
            throw AppException.unauthenticated("invalid credentials");
        }

        user.setFailedAttempts(0);
        user.setLockedUntilEpochMs(null);
        user.setLastLoginAt(now);
        users.saveAll(replaceUser(users.loadAll(), user));

        List<String> roles = user.getRoles().isEmpty() ? List.of("viewer") : user.getRoles();
        SessionStore.SessionInfo session = sessions.create(user.getUsername(), roles);
        audit.append("auth", user.getUsername(), "login", "user:" + user.getUsername(), "success",
                Map.of("roles", roles));
        return new LoginResult(session.token(), session.csrfToken(), user.getUsername(),
                user.getDisplayName(), roles);
    }

    public void logout(String token, String operator) {
        sessions.invalidate(token);
        if (operator != null) {
            audit.append("auth", operator, "logout", "session", "success", Map.of());
        }
    }

    public String newCsrfToken() {
        return tokens.csrfToken();
    }

    private void validatePassword(String password) {
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

    private List<UserAccount> replaceUser(List<UserAccount> all, UserAccount updated) {
        List<UserAccount> result = new java.util.ArrayList<>(all.size());
        for (UserAccount u : all) {
            result.add(u.getUsername().equals(updated.getUsername()) ? updated : u);
        }
        return result;
    }

    public record LoginResult(String sessionToken, String csrfToken, String username,
                              String displayName, List<String> roles) {
    }
}
