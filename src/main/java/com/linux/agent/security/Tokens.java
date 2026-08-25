package com.linux.agent.security;

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

    public String shortId() {
        byte[] bytes = new byte[4];
        random.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(8);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String randomToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
