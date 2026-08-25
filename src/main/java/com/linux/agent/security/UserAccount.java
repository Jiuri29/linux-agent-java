package com.linux.agent.security;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class UserAccount {

    @NotBlank
    @Pattern(regexp = "^[a-zA-Z0-9_.-]{3,32}$", message = "username must match ^[a-zA-Z0-9_.-]{3,32}$")
    private String username;

    private String displayName;

    private String passwordHash;

    private List<String> roles = new ArrayList<>();

    private boolean enabled = true;

    private Instant createdAt;

    private Instant lastLoginAt;

    private int failedAttempts;

    private Long lockedUntilEpochMs;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDisplayName() {
        return displayName == null || displayName.isBlank() ? username : displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles == null ? new ArrayList<>() : new ArrayList<>(roles);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(Instant lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public void setFailedAttempts(int failedAttempts) {
        this.failedAttempts = failedAttempts;
    }

    public Long getLockedUntilEpochMs() {
        return lockedUntilEpochMs;
    }

    public void setLockedUntilEpochMs(Long lockedUntilEpochMs) {
        this.lockedUntilEpochMs = lockedUntilEpochMs;
    }
}
