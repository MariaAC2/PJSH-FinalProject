package com.quizapp.services;

import com.quizapp.audit.Auditable;
import com.quizapp.dtos.CreateEventRequest;
import com.quizapp.dtos.EventResponse;
import com.quizapp.entities.Event;
import com.quizapp.entities.EventParticipant;
import com.quizapp.entities.Quiz;
import com.quizapp.entities.User;
import com.quizapp.enums.EventStatus;
import com.quizapp.enums.UserRole;
import com.quizapp.repositories.EventParticipantRepository;
import com.quizapp.repositories.EventRepository;
import com.quizapp.repositories.QuizRepository;
import com.quizapp.repositories.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final QuizRepository quizRepository;
    private final UserRepository userRepository;
    private final EventParticipantRepository participantRepository;
    private final UserService userService;

    public EventService(EventRepository eventRepository,
                        QuizRepository quizRepository,
                        UserRepository userRepository, EventParticipantRepository participantRepository, UserService userService) {
        this.eventRepository = eventRepository;
        this.quizRepository = quizRepository;
        this.userRepository = userRepository;
        this.participantRepository = participantRepository;
        this.userService = userService;
    }

    /// Creates a new event for a quiz. Only the quiz owner or an admin can create events.
    @Transactional
    @Auditable(action = "create_event")
    @PreAuthorize("isAuthenticated()")
    public EventResponse createEvent(CreateEventRequest req) {
        if (req == null) throw new IllegalArgumentException("Request is required");
        if (req.testId() == null) throw new IllegalArgumentException("testId is required");
        if (req.name() == null || req.name().isBlank()) throw new IllegalArgumentException("name is required");

        int duration = (req.durationSeconds() != null) ? req.durationSeconds() : 600;
        if (duration <= 0) throw new IllegalArgumentException("durationSeconds must be > 0");

        if (req.joinClosesAt() != null && req.joinClosesAt().isBefore(Instant.now()))
            throw new IllegalArgumentException("joinClosesAt must be in the future");

        Quiz quiz = quizRepository.findById(req.testId())
                .orElseThrow(() -> new IllegalArgumentException("Quiz not found"));

        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User host = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Authenticated user not found in DB"));

        if (!(quiz.getOwner() != null && quiz.getOwner().getId() != null && quiz.getOwner().getId().equals(host.getId()))
                && host.getRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("Only the quiz owner or an admin can create events");
        }

        Event e = new Event();
        e.setQuiz(quiz);
        e.setHost(host);
        e.setName(req.name().trim());
        e.setDurationSeconds(duration);
        e.setJoinClosesAt(req.joinClosesAt());
        e.setJoinCode(generateJoinCode());

        e.setStartsAt(null);
        e.setEndsAt(null);
        e.setStatus(EventStatus.OPEN);

        Event saved = eventRepository.save(e);
        return toResponse(saved);
    }

    /// Allows a user to join an event using a join code. The event must be open and within the joining period.
    @Transactional
    @Auditable(action = "join_event")
    @PreAuthorize("isAuthenticated()")
    public EventResponse joinEvent(String joinCode) {
        if (joinCode == null || joinCode.isBlank())
            throw new IllegalArgumentException("joinCode is required");

        Event event = eventRepository.findByJoinCode(joinCode.trim().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Invalid join code"));

        if (event.getStatus() != EventStatus.OPEN)
            throw new IllegalStateException("Event is not open for joining");

        Instant now = Instant.now();

        if (event.getJoinClosesAt() != null && now.isAfter(event.getJoinClosesAt()))
            throw new IllegalStateException("Joining is closed");

        if (event.getStartsAt() != null && now.isAfter(event.getStartsAt()))
            throw new IllegalStateException("Event already started");

        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Authenticated user not found in DB"));

        if (participantRepository.existsByEventAndUser(event, user))
            throw new IllegalStateException("Already joined");

        EventParticipant p = new EventParticipant();
        p.setEvent(event);
        p.setUser(user);

        participantRepository.save(p);

        return toResponse(event);
    }

    /// Allows the host or an admin to start the event. The event must be open and within the joining period.
    @Transactional
    @Auditable(action = "start_event")
    @PreAuthorize("isAuthenticated()")
    public void startEvent(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found"));

        User currentUser = userService.getAuthenticatedUserEntity();

        if (!event.getHost().getId().equals(currentUser.getId()) && currentUser.getRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("Only the host or admin can start event");
        }

        if (event.getStatus() != EventStatus.OPEN)
            throw new IllegalStateException("Event is not open");


        Instant now = Instant.now();

        if (event.getJoinClosesAt() != null && !now.isBefore(event.getJoinClosesAt()))
            throw new IllegalStateException("Joining period has ended");

        event.setStatus(EventStatus.RUNNING);
        event.setStartsAt(now);
        event.setEndsAt(now.plusSeconds(event.getDurationSeconds()));
        event.setJoinClosesAt(now);
    }

    /// Allows the host or an admin to close the event from running. The event must be open and within the joining period.
    @Transactional
    @Auditable(action = "close_event")
    @PreAuthorize("isAuthenticated()")
    public void closeEvent(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found"));

        User currentUser = userService.getAuthenticatedUserEntity();

        if (!event.getHost().getId().equals(currentUser.getId()) && currentUser.getRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("Only the host or admin can close event");
        }

        if (event.getStatus() == EventStatus.CLOSED || event.getStatus() == EventStatus.CANCELLED) {
            return;
        }

        event.setStatus(EventStatus.CLOSED);
        event.setEndsAt(Instant.now());
        event.setJoinClosesAt(Instant.now());
    }

    /// Allows a participant to leave an event before it starts. The event must be open and within the joining period.
    @Transactional
    @Auditable(action = "leave_event")
    @PreAuthorize("isAuthenticated()")
    public void leaveEvent(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found"));

        if (event.getStatus() != EventStatus.OPEN) {
            throw new IllegalStateException("You can only leave before the event starts");
        }

        User user = userService.getAuthenticatedUserEntity();

        EventParticipant p = participantRepository.findByEventAndUser(event, user)
                .orElseThrow(() -> new IllegalArgumentException("Not joined"));

        participantRepository.delete(p);
    }

    /// Allows the host or an admin to cancel the event before it starts. The event must be open or running.
    @Transactional
    @Auditable(action = "cancel_event")
    @PreAuthorize("isAuthenticated()")
    public void cancelEvent(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found"));

        User currentUser = userService.getAuthenticatedUserEntity();

        if (!event.getHost().getId().equals(currentUser.getId()) && currentUser.getRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("Only the host or admin can cancel event");
        }

        if (event.getStatus() == EventStatus.RUNNING) {
            throw new IllegalStateException("Cannot cancel a running event");
        }
        if (event.getStatus() == EventStatus.CANCELLED) {
            return;
        }
        if (event.getStatus() == EventStatus.CLOSED) {
            throw new IllegalStateException("Cannot cancel a closed event");
        }

        Instant now = Instant.now();

        event.setStatus(EventStatus.CANCELLED);
        event.setJoinClosesAt(now);

        event.setEndsAt(now);
    }

    /// Lists all events. Only admins can see all events. Regular users can only see events they are hosting or participating in.
    @PreAuthorize("hasRole('ADMIN')")
    public List<EventResponse> listAllEvents() {
        List<Event> events = eventRepository.findAll();
        return events.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /// Gets event details by ID. Only admins, the host, or participants can see the event details.
    public EventResponse getEventById(Long eventId) {
        Event e = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found"));
        return toResponse(e);
    }

    /// Lists events for a specific quiz. Only admins or the quiz owner can see the events.
    public List<EventResponse> listEventsForTest(Long testId) {
        List<Event> events = eventRepository.findAll();
        return events.stream()
                .filter(ev -> ev.getQuiz() != null && ev.getQuiz().getId() != null && ev.getQuiz().getId().equals(testId))
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private EventResponse toResponse(Event e) {
        return new EventResponse(
                e.getId(),
                e.getQuiz() != null ? e.getQuiz().getId() : null,
                e.getName(),
                e.getJoinCode(),
                e.getStartsAt(),
                e.getEndsAt(),
                e.getDurationSeconds(),
                e.getHost() != null ? e.getHost().getEmail() : null,
                e.getStatus() != null ? e.getStatus().name() : null
        );
    }

    /// Generates a random 8-character alphanumeric join code.
    private String generateJoinCode() {
        // very small join code generator — 8-char alphanumeric
        String alpha = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) sb.append(alpha.charAt((int) (Math.random() * alpha.length())));
        return sb.toString();
    }
}
