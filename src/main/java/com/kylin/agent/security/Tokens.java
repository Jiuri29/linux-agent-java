package com.kylin.agent.security;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

@Component
public class Tokens {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom random = new SecureRandom();

    public String sessionToken() {
        return "sess-" + randomToken();
    }

    public String csrfToken() {
        return "csrf-" + randomToken();
    }

    private String randomToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
