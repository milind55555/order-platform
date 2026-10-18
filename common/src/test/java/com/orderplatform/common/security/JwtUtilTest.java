package com.orderplatform.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-1234";

    @Test
    void generatedTokenCanBeParsed() {
        JwtUtil jwt = new JwtUtil(SECRET, 60_000);

        Claims claims = jwt.parse(jwt.generateToken("alice", "USER"));

        assertThat(claims.getSubject()).isEqualTo("alice");
        assertThat(claims.get(JwtUtil.ROLE_CLAIM, String.class)).isEqualTo("USER");
    }

    @Test
    void tokenSignedWithDifferentSecretIsRejected() {
        JwtUtil issuer = new JwtUtil(SECRET, 60_000);
        JwtUtil other = new JwtUtil("another-secret-another-secret-123456", 60_000);

        String token = issuer.generateToken("alice", "USER");

        assertThatThrownBy(() -> other.parse(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void expiredTokenIsRejected() {
        JwtUtil jwt = new JwtUtil(SECRET, -1_000);

        String token = jwt.generateToken("alice", "USER");

        assertThatThrownBy(() -> jwt.parse(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void shortSecretIsRejected() {
        assertThatThrownBy(() -> new JwtUtil("short", 1000)).isInstanceOf(IllegalArgumentException.class);
    }
}
