package com.quizapp.dtos;

import com.quizapp.enums.UserRole;

public record UserResponse(
        Long id,
        String email,
        String displayName,
        UserRole role
) {}

