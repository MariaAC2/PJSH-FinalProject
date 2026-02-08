package com.quizapp.dtos;

public record UserUpdateRequest(
        String displayName,
        String password // optional: null/blank means "don't change"
) {}