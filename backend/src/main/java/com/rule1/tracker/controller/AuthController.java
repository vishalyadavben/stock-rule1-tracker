package com.rule1.tracker.controller;

import com.rule1.tracker.dto.AuthDtos.*;
import com.rule1.tracker.entity.User;
import com.rule1.tracker.repository.UserRepository;
import com.rule1.tracker.security.JwtUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest req) {
        if (userRepository.findByEmail(req.email()).isPresent()) {
            return ResponseEntity.badRequest().body("Email already registered");
        }
        User user = new User();
        user.setEmail(req.email());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setDisplayName(req.displayName());
        user.setCreatedAt(LocalDateTime.now());
        user = userRepository.save(user);

        String token = jwtUtil.generateToken(user.getEmail(), user.getId());
        return ResponseEntity.ok(new AuthResponse(token, user.getEmail(), user.getId()));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        var userOpt = userRepository.findByEmail(req.email());
        if (userOpt.isEmpty() || !passwordEncoder.matches(req.password(), userOpt.get().getPasswordHash())) {
            return ResponseEntity.status(401).body("Invalid email or password");
        }
        User user = userOpt.get();
        String token = jwtUtil.generateToken(user.getEmail(), user.getId());
        return ResponseEntity.ok(new AuthResponse(token, user.getEmail(), user.getId()));
    }
}
