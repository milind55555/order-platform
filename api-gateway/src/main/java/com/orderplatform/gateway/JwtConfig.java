package com.orderplatform.gateway;

import com.orderplatform.common.security.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtConfig {

    @Bean
    public JwtUtil jwtUtil(@Value("${jwt.secret}") String secret,
                           @Value("${jwt.expiration-ms:3600000}") long expirationMs) {
        return new JwtUtil(secret, expirationMs);
    }
}
