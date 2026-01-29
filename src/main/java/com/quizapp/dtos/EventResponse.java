package com.quizapp.dtos;

import java.time.Instant;

public record EventResponse(
        Long id,
        Long testId,
        String name,
        Instant startsAt,
        Instant endsAt,
        Integer durationSeconds,
        Integer capacity,
        String hostEmail,
        String status
) { }

