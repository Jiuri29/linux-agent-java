package com.kylin.agent.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionStore {

    public record SessionInfo(
            String token,
            String username,
            List<String> roles,
            String csrfToken,
            Instant createdAt,
            Instant expiresAt) {
    }

    private static final Duration DEFAULT_TTL = Duration.ofHours(8);

    private final Map<String, SessionInfo> sessions = new ConcurrentHashMap<>();
    private final Duration ttl;
    private final Tokens tokens;

    @Autowired
    public SessionStore(Tokens tokens) {
        this(DEFAULT_TTL, tokens);
    }

    SessionStore(Duration ttl, Tokens tokens) {
        this.ttl = ttl;
        this.tokens = tokens;
    }

    public SessionInfo create(String username, List<String> roles) {
        Instant now = Instant.now();
        String csrfToken = tokens.csrfToken();
        SessionInfo session = new SessionInfo(tokens.sessionToken(), username, List.copyOf(roles),
                csrfToken, now, now.plus(ttl));
        sessions.put(session.token(), session);
        purgeExpired(now);
        return session;
    }

    public SessionInfo get(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        SessionInfo session = sessions.get(token);
        if (session == null) {
            return null;
        }
        Instant now = Instant.now();
        if (now.isAfter(session.expiresAt())) {
            sessions.remove(token);
            return null;
        }
        SessionInfo refreshed = new SessionInfo(session.token(), session.username(), session.roles(),
                session.csrfToken(), session.createdAt(), now.plus(ttl));
        sessions.put(token, refreshed);
        return refreshed;
    }

    public void invalidate(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

    public int size() {
        purgeExpired(Instant.now());
        return sessions.size();
    }

    private void purgeExpired(Instant now) {
        sessions.entrySet().removeIf(e -> now.isAfter(e.getValue().expiresAt()));
    }
}
