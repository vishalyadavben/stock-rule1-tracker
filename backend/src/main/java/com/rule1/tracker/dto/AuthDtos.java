package com.rule1.tracker.dto;

public class AuthDtos {
    public record RegisterRequest(String email, String password, String displayName) {}
    public record LoginRequest(String email, String password) {}
    public record AuthResponse(String token, String email, Long userId, String displayName) {}
}
