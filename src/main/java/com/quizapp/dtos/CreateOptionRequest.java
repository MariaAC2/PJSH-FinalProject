package com.quizapp.dtos;

public record CreateOptionRequest(
        String text,
        boolean correct
) { }
