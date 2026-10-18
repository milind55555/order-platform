package com.orderplatform.auth;

import com.orderplatform.common.security.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security setup for the auth service: stateless, BCrypt password hashing.
 * Token validation for the rest of the platform happens at the API gateway.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtUtil jwtUtil(@Value("${jwt.secret}") String secret,
                           @Value("${jwt.expiration-ms}") long expirationMs) {
        return new JwtUtil(secret, expirationMs);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**", "/actuator/**").permitAll()
                        .anyRequest().denyAll());
        return http.build();
    }

    /** Seeds an admin account so product management can be demonstrated. */
    @Bean
    public CommandLineRunner seedAdmin(UserRepository users, PasswordEncoder encoder,
                                       @Value("${app.admin.username}") String username,
                                       @Value("${app.admin.password}") String password) {
        return args -> {
            if (!users.existsByUsername(username)) {
                users.save(new User(username, encoder.encode(password), "ADMIN"));
            }
        };
    }
}
