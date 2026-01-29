package com.quizapp.dtos;

public record OptionResponse(
        Long id,
        String text,
        boolean correct
) { }

