package com.quizapp.services;

import com.quizapp.dtos.LeaderboardEntry;
import com.quizapp.entities.Attempt;
import com.quizapp.repositories.AttemptRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class LeaderboardService {

    private final AttemptRepository attemptRepository;

    public LeaderboardService(AttemptRepository attemptRepository) {
        this.attemptRepository = attemptRepository;
    }

    public List<LeaderboardEntry> topForEvent(Long eventId, int limit) {
        List<Attempt> attempts = attemptRepository.findTopSubmittedByEventId(eventId, PageRequest.of(0, limit));
        return attempts.stream()
                .map(a -> new LeaderboardEntry(
                        a.getParticipant().getUser().getId(),
                        a.getParticipant().getUser().getDisplayName(),
                        a.getScore(),
                        a.getMaxScore(),
                        a.getSubmittedAt()
                ))
                .collect(Collectors.toList());
    }
}

