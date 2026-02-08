package com.quizapp.dtos;

import java.time.Instant;

public record EventResponse(
        Long id,
        Long testId,
        String name,
        String joinCode,
        Instant startsAt,
        Instant endsAt,
        Integer durationSeconds,
        String hostEmail,
        String status
) { }

