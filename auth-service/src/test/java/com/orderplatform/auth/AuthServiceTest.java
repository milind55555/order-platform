package com.orderplatform.auth;

import com.orderplatform.auth.AuthDtos.LoginRequest;
import com.orderplatform.auth.AuthDtos.RegisterRequest;
import com.orderplatform.auth.AuthDtos.TokenResponse;
import com.orderplatform.auth.AuthDtos.UserResponse;
import com.orderplatform.common.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private final JwtUtil jwtUtil = new JwtUtil("auth-test-secret-auth-test-secret-123", 60_000);
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, encoder, jwtUtil);
    }

    @Test
    void registerStoresHashedPassword() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = authService.register(new RegisterRequest("alice", "password123"));

        assertThat(response.username()).isEqualTo("alice");
        assertThat(response.role()).isEqualTo("USER");
        verify(userRepository).save(org.mockito.ArgumentMatchers.argThat(
                u -> !u.getPasswordHash().equals("password123") && encoder.matches("password123", u.getPasswordHash())));
    }

    @Test
    void registerRejectsDuplicateUsername() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest("alice", "password123")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already taken");
        verify(userRepository, never()).save(any());
    }

    @Test
    void loginReturnsVerifiableToken() {
        User user = new User("alice", encoder.encode("password123"), "USER");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        TokenResponse response = authService.login(new LoginRequest("alice", "password123"));

        assertThat(jwtUtil.parse(response.token()).getSubject()).isEqualTo("alice");
    }

    @Test
    void loginWithWrongPasswordIsRejected() {
        User user = new User("alice", encoder.encode("password123"), "USER");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice", "wrong-password")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid credentials");
    }
}
