package com.ahealth.backend.security;

public record AuthenticatedUser(
    long userId,
    String token,
    String name,
    String email
) {}
