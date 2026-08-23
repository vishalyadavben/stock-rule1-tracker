package com.rule1.tracker.security;

import org.springframework.security.core.context.SecurityContextHolder;

public class CurrentUser {
    public static Long id() {
        var principal = (JwtAuthFilter.AuthenticatedUser) SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();
        return principal.userId();
    }
}
