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
        return ResponseEntity.ok(new AuthResponse(token, user.getEmail(), user.getId(), user.getDisplayName()));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        var userOpt = userRepository.findByEmail(req.email());
        if (userOpt.isEmpty() || !passwordEncoder.matches(req.password(), userOpt.get().getPasswordHash())) {
            return ResponseEntity.status(401).body("Invalid email or password");
        }
        User user = userOpt.get();
        String token = jwtUtil.generateToken(user.getEmail(), user.getId());
        return ResponseEntity.ok(new AuthResponse(token, user.getEmail(), user.getId(), user.getDisplayName()));
    }

    /** Lets the frontend re-fetch the logged-in user's display name after a page reload,
     *  without needing to trust whatever was last cached in localStorage.
     *  Note: /api/auth/** is publicly reachable per SecurityConfig, so this endpoint must
     *  check for a valid token itself rather than relying on the URL pattern for protection. */
    @GetMapping("/me")
    public ResponseEntity<?> me() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof com.rule1.tracker.security.JwtAuthFilter.AuthenticatedUser principal)) {
            return ResponseEntity.status(401).build();
        }
        return userRepository.findById(principal.userId())
                .map(u -> ResponseEntity.ok(new AuthResponse(null, u.getEmail(), u.getId(), u.getDisplayName())))
                .orElse(ResponseEntity.notFound().build());
    }
}
