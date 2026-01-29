package com.quizapp.services;

import com.quizapp.audit.Auditable;
import com.quizapp.dtos.CreateEventRequest;
import com.quizapp.dtos.EventResponse;
import com.quizapp.entities.Event;
import com.quizapp.entities.Test;
import com.quizapp.entities.User;
import com.quizapp.enums.UserRole;
import com.quizapp.repositories.EventRepository;
import com.quizapp.repositories.TestRepository;
import com.quizapp.repositories.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final TestRepository testRepository;
    private final UserRepository userRepository;

    public EventService(EventRepository eventRepository,
                        TestRepository testRepository,
                        UserRepository userRepository) {
        this.eventRepository = eventRepository;
        this.testRepository = testRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    @Auditable(action = "create_event")
    public EventResponse createEvent(CreateEventRequest req) {
        if (req == null) throw new IllegalArgumentException("Request is required");
        if (req.testId() == null) throw new IllegalArgumentException("testId is required");
        if (req.startsAt() == null || req.endsAt() == null)
            throw new IllegalArgumentException("startsAt and endsAt are required");
        if (!req.startsAt().isBefore(req.endsAt()))
            throw new IllegalArgumentException("startsAt must be before endsAt");

        Test test = testRepository.findById(req.testId())
                .orElseThrow(() -> new IllegalArgumentException("Test not found"));

        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User host = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Authenticated user not found in DB"));

        // allow only test owner or ADMIN
        if (!(test.getOwner() != null && test.getOwner().getId() != null && test.getOwner().getId().equals(host.getId()))
                && host.getRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("Only the test owner or an admin can create events");
        }

        Event e = new Event();
        e.setTest(test);
        e.setHost(host);
        e.setStartsAt(req.startsAt());
        e.setEndsAt(req.endsAt());
        if (req.durationSeconds() != null) e.setDurationSeconds(req.durationSeconds());
        // generate a simple join code
        e.setJoinCode(generateJoinCode());

        Event saved = eventRepository.save(e);

        return toResponse(saved);
    }

    public List<EventResponse> listEventsForTest(Long testId) {
        List<Event> events = eventRepository.findAll();
        return events.stream()
                .filter(ev -> ev.getTest() != null && ev.getTest().getId() != null && ev.getTest().getId().equals(testId))
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private EventResponse toResponse(Event e) {
        return new EventResponse(
                e.getId(),
                e.getTest() != null ? e.getTest().getId() : null,
                null,
                e.getStartsAt(),
                e.getEndsAt(),
                e.getDurationSeconds(),
                null,
                e.getHost() != null ? e.getHost().getEmail() : null,
                e.getStatus() != null ? e.getStatus().name() : null
        );
    }

    private String generateJoinCode() {
        // very small join code generator — 8-char alphanumeric
        String alpha = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) sb.append(alpha.charAt((int) (Math.random() * alpha.length())));
        return sb.toString();
    }
}
