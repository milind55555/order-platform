package com.orderplatform.auth;

import com.orderplatform.auth.AuthDtos.LoginRequest;
import com.orderplatform.auth.AuthDtos.RegisterRequest;
import com.orderplatform.auth.AuthDtos.TokenResponse;
import com.orderplatform.auth.AuthDtos.UserResponse;
import com.orderplatform.common.security.JwtUtil;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    public static final String ROLE_USER = "USER";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already taken");
        }
        User saved = userRepository.save(
                new User(request.username(), passwordEncoder.encode(request.password()), ROLE_USER));
        return new UserResponse(saved.getId(), saved.getUsername(), saved.getRole());
    }

    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        String token = jwtUtil.generateToken(user.getUsername(), user.getRole());
        return new TokenResponse(token, "Bearer", user.getUsername(), user.getRole());
    }
}
