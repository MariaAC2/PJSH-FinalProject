package com.quizapp.services;

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
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock private EventRepository eventRepository;
    @Mock private QuizRepository quizRepository;
    @Mock private UserRepository userRepository;
    @Mock private EventParticipantRepository participantRepository;
    @Mock private UserService userService;

    @InjectMocks private EventService eventService;

    @BeforeEach
    void setUpSecurityContext() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("host@test.com", "N/A"));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // --------------------------
    // createEvent
    // --------------------------

    @Test
    void createEvent_shouldCreateOpenEvent_whenValid_andHostIsOwner() {
        // arrange
        User host = user(1L, "host@test.com", UserRole.USER);
        Quiz quiz = quiz(10L, host);

        CreateEventRequest req = new CreateEventRequest(
                quiz.getId(),
                "Friday Quiz",
                300,
                Instant.now().plusSeconds(3600) // join closes in the future
        );

        when(quizRepository.findById(quiz.getId())).thenReturn(Optional.of(quiz));
        when(userRepository.findByEmail("host@test.com")).thenReturn(Optional.of(host));
        when(eventRepository.save(any(Event.class))).thenAnswer(inv -> {
            Event e = inv.getArgument(0);
            e.setId(99L);
            return e;
        });

        // act
        EventResponse resp = eventService.createEvent(req);

        // assert
        assertNotNull(resp);
        assertEquals(99L, resp.id());
        assertEquals(quiz.getId(), resp.testId());
        assertEquals("Friday Quiz", resp.name());
        assertNotNull(resp.joinCode());
        assertEquals(EventStatus.OPEN.name(), resp.status());

        verify(eventRepository).save(any(Event.class));
    }

    @Test
    void createEvent_shouldThrow_whenJoinClosesInPast() {
        CreateEventRequest req = new CreateEventRequest(
                10L, "Name", 600, Instant.now().minusSeconds(5)
        );
        assertThrows(IllegalArgumentException.class, () -> eventService.createEvent(req));
    }

    @Test
    void createEvent_shouldThrowAccessDenied_whenNotOwner_andNotAdmin() {
        User quizOwner = user(1L, "owner@test.com", UserRole.USER);
        Quiz quiz = quiz(10L, quizOwner);

        User host = user(2L, "host@test.com", UserRole.USER);

        CreateEventRequest req = new CreateEventRequest(10L, "Name", 600, null);

        when(quizRepository.findById(10L)).thenReturn(Optional.of(quiz));
        when(userRepository.findByEmail("host@test.com")).thenReturn(Optional.of(host));

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> eventService.createEvent(req));
    }

    // --------------------------
    // joinEvent
    // --------------------------

    @Test
    void joinEvent_shouldAddParticipant_whenOpen_andWithinJoinWindow() {
        // arrange
        User host = user(1L, "host@test.com", UserRole.USER);
        User joiner = user(2L, "host@test.com", UserRole.USER); // authentication name = host@test.com
        Quiz quiz = quiz(10L, host);

        Event event = new Event();
        event.setId(100L);
        event.setQuiz(quiz);
        event.setHost(host);
        event.setStatus(EventStatus.OPEN);
        event.setJoinCode("ABCDEFGH");
        event.setJoinClosesAt(Instant.now().plusSeconds(120));
        event.setStartsAt(null);

        when(eventRepository.findByJoinCode("ABCDEFGH")).thenReturn(Optional.of(event));
        when(userRepository.findByEmail("host@test.com")).thenReturn(Optional.of(joiner));
        when(participantRepository.existsByEventAndUser(event, joiner)).thenReturn(false);

        // act
        EventResponse resp = eventService.joinEvent("ABCDEFGH");

        // assert
        assertEquals(event.getId(), resp.id());
        verify(participantRepository).save(any(EventParticipant.class));
    }

    @Test
    void joinEvent_shouldThrow_whenEventNotOpen() {
        Event event = new Event();
        event.setStatus(EventStatus.RUNNING);

        when(eventRepository.findByJoinCode(anyString())).thenReturn(Optional.of(event));

        assertThrows(IllegalStateException.class, () -> eventService.joinEvent("CODE"));
    }

    @Test
    void joinEvent_shouldThrow_whenJoiningClosedByTime() {
        Event event = new Event();
        event.setStatus(EventStatus.OPEN);
        event.setJoinClosesAt(Instant.now().minusSeconds(1));

        when(eventRepository.findByJoinCode(anyString())).thenReturn(Optional.of(event));

        assertThrows(IllegalStateException.class, () -> eventService.joinEvent("CODE"));
    }

    @Test
    void joinEvent_shouldThrow_whenAlreadyJoined() {
        Event event = new Event();
        event.setStatus(EventStatus.OPEN);
        event.setJoinClosesAt(Instant.now().plusSeconds(60));

        User user = user(1L, "host@test.com", UserRole.USER);

        when(eventRepository.findByJoinCode(anyString())).thenReturn(Optional.of(event));
        when(userRepository.findByEmail("host@test.com")).thenReturn(Optional.of(user));
        when(participantRepository.existsByEventAndUser(event, user)).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> eventService.joinEvent("CODE"));
    }

    // --------------------------
    // startEvent
    // --------------------------

    @Test
    void startEvent_shouldSetRunningAndTimes_whenHost_andOpen() {
        User host = user(1L, "host@test.com", UserRole.USER);
        Quiz quiz = quiz(10L, host);

        Event event = new Event();
        event.setId(200L);
        event.setHost(host);
        event.setQuiz(quiz);
        event.setStatus(EventStatus.OPEN);
        event.setDurationSeconds(600);
        event.setJoinClosesAt(Instant.now().plusSeconds(60));

        when(eventRepository.findById(200L)).thenReturn(Optional.of(event));
        when(userService.getAuthenticatedUserEntity()).thenReturn(host);

        // act
        eventService.startEvent(200L);

        // assert
        assertEquals(EventStatus.RUNNING, event.getStatus());
        assertNotNull(event.getStartsAt());
        assertNotNull(event.getEndsAt());
        assertNotNull(event.getJoinClosesAt());
        assertFalse(event.getEndsAt().isBefore(event.getStartsAt()));
    }

    @Test
    void startEvent_shouldThrow_whenNotOpen() {
        User host = user(1L, "host@test.com", UserRole.USER);

        Event event = new Event();
        event.setHost(host);
        event.setStatus(EventStatus.CLOSED);

        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(userService.getAuthenticatedUserEntity()).thenReturn(host);

        assertThrows(IllegalStateException.class, () -> eventService.startEvent(1L));
    }

    // --------------------------
    // closeEvent
    // --------------------------

    @Test
    void closeEvent_shouldSetClosed_whenRunning() {
        User host = user(1L, "host@test.com", UserRole.USER);

        Event event = new Event();
        event.setId(300L);
        event.setHost(host);
        event.setStatus(EventStatus.RUNNING);

        when(eventRepository.findById(300L)).thenReturn(Optional.of(event));
        when(userService.getAuthenticatedUserEntity()).thenReturn(host);

        eventService.closeEvent(300L);

        assertEquals(EventStatus.CLOSED, event.getStatus());
        assertNotNull(event.getEndsAt());
        assertNotNull(event.getJoinClosesAt());
    }

    @Test
    void closeEvent_shouldBeIdempotent_whenAlreadyClosed() {
        User host = user(1L, "host@test.com", UserRole.USER);

        Event event = new Event();
        event.setHost(host);
        event.setStatus(EventStatus.CLOSED);

        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(userService.getAuthenticatedUserEntity()).thenReturn(host);

        assertDoesNotThrow(() -> eventService.closeEvent(1L));
    }

    // --------------------------
    // leaveEvent
    // --------------------------

    @Test
    void leaveEvent_shouldDeleteParticipant_whenOpen() {
        User host = user(1L, "host@test.com", UserRole.USER);
        Event event = new Event();
        event.setId(400L);
        event.setStatus(EventStatus.OPEN);

        User user = user(2L, "u@test.com", UserRole.USER);
        EventParticipant participant = new EventParticipant();
        participant.setEvent(event);
        participant.setUser(user);

        when(eventRepository.findById(400L)).thenReturn(Optional.of(event));
        when(userService.getAuthenticatedUserEntity()).thenReturn(user);
        when(participantRepository.findByEventAndUser(event, user)).thenReturn(Optional.of(participant));

        eventService.leaveEvent(400L);

        verify(participantRepository).delete(participant);
    }

    @Test
    void leaveEvent_shouldThrow_whenNotOpen() {
        Event event = new Event();
        event.setStatus(EventStatus.RUNNING);

        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));

        assertThrows(IllegalStateException.class, () -> eventService.leaveEvent(1L));
    }

    // --------------------------
    // cancelEvent
    // --------------------------

    @Test
    void cancelEvent_shouldSetCancelled_whenOpen() {
        User host = user(1L, "host@test.com", UserRole.USER);

        Event event = new Event();
        event.setId(500L);
        event.setHost(host);
        event.setStatus(EventStatus.OPEN);

        when(eventRepository.findById(500L)).thenReturn(Optional.of(event));
        when(userService.getAuthenticatedUserEntity()).thenReturn(host);

        eventService.cancelEvent(500L);

        assertEquals(EventStatus.CANCELLED, event.getStatus());
        assertNotNull(event.getEndsAt());
        assertNotNull(event.getJoinClosesAt());
    }

    @Test
    void cancelEvent_shouldBeIdempotent_whenAlreadyCancelled() {
        User host = user(1L, "host@test.com", UserRole.USER);

        Event event = new Event();
        event.setHost(host);
        event.setStatus(EventStatus.CANCELLED);

        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(userService.getAuthenticatedUserEntity()).thenReturn(host);

        assertDoesNotThrow(() -> eventService.cancelEvent(1L));
    }

    @Test
    void cancelEvent_shouldThrow_whenClosed() {
        User host = user(1L, "host@test.com", UserRole.USER);

        Event event = new Event();
        event.setHost(host);
        event.setStatus(EventStatus.CLOSED);

        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(userService.getAuthenticatedUserEntity()).thenReturn(host);

        assertThrows(IllegalStateException.class, () -> eventService.cancelEvent(1L));
    }

    // --------------------------
    // helpers
    // --------------------------

    private static User user(Long id, String email, UserRole role) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        u.setDisplayName("X");
        u.setRole(role);
        return u;
    }

    private static Quiz quiz(Long id, User owner) {
        Quiz q = new Quiz();
        q.setId(id);
        q.setOwner(owner);
        q.setTitle("Quiz");
        q.setDescription("Desc");
        return q;
    }
}
