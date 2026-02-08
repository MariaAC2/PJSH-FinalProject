package com.quizapp.services;

import com.quizapp.dtos.LeaderboardEntry;
import com.quizapp.entities.Attempt;
import com.quizapp.entities.EventParticipant;
import com.quizapp.entities.User;
import com.quizapp.repositories.AttemptRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaderboardServiceTest {

    @Mock AttemptRepository attemptRepository;

    @InjectMocks LeaderboardService leaderboardService;

    @Test
    void topForEvent_mapsAttemptsToEntries() {
        User user = new User();
        user.setId(42L);
        user.setDisplayName("Ada Lovelace");

        EventParticipant participant = new EventParticipant();
        participant.setUser(user);

        Attempt attempt = new Attempt();
        attempt.setParticipant(participant);
        attempt.setScore(8);
        attempt.setMaxScore(10);
        Instant submittedAt = Instant.parse("2024-02-02T10:00:00Z");
        attempt.setSubmittedAt(submittedAt);

        when(attemptRepository.findTopSubmittedByEventId(5L, PageRequest.of(0, 3)))
                .thenReturn(List.of(attempt));

        List<LeaderboardEntry> entries = leaderboardService.topForEvent(5L, 3);

        assertEquals(1, entries.size());
        LeaderboardEntry entry = entries.get(0);
        assertEquals(42L, entry.userId());
        assertEquals("Ada Lovelace", entry.displayName());
        assertEquals(8, entry.score());
        assertEquals(10, entry.maxScore());
        assertEquals(submittedAt, entry.submittedAt());
    }

    @Test
    void topForEvent_usesPageRequestLimit() {
        when(attemptRepository.findTopSubmittedByEventId(7L, PageRequest.of(0, 5)))
                .thenReturn(List.of());

        leaderboardService.topForEvent(7L, 5);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(attemptRepository).findTopSubmittedByEventId(eq(7L), pageableCaptor.capture());

        Pageable pageable = pageableCaptor.getValue();
        assertEquals(0, pageable.getPageNumber());
        assertEquals(5, pageable.getPageSize());
    }
}
