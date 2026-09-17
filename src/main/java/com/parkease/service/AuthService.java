package com.parkease.service;

import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.parkease.dto.AuthResponse;
import com.parkease.dto.LoginRequest;
import com.parkease.dto.RegisterRequest;
import com.parkease.entity.User;
import com.parkease.exception.ConflictException;
import com.parkease.exception.UnauthorizedException;
import com.parkease.repository.UserRepository;
import com.parkease.security.JwtService;

@Service
public class AuthService {

    private static final String INVALID_CREDENTIALS = "Invalid email or password";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.getEmail());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Email is already registered");
        }

        User user = userRepository.save(new User(
                request.getName().trim(), email, passwordEncoder.encode(request.getPassword())));
        return toResponse(user, jwtService.generateToken(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.getEmail());
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UnauthorizedException(INVALID_CREDENTIALS));
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }
        return toResponse(user, jwtService.generateToken(user));
    }

    private AuthResponse toResponse(User user, String token) {
        return new AuthResponse(token, user.getId(), user.getName(), user.getEmail(), user.getRole());
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}