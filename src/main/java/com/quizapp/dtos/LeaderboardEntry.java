package com.quizapp.dtos;

import java.time.Instant;

public record LeaderboardEntry(
        Long userId,
        String displayName,
        Integer score,
        Integer maxScore,
        Instant submittedAt
) {}

