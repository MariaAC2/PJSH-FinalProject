package com.quizapp.controllers;

import com.quizapp.dtos.CreateEventRequest;
import com.quizapp.dtos.EventResponse;
import com.quizapp.dtos.JoinEventRequest;
import com.quizapp.dtos.LeaderboardEntry;
import com.quizapp.services.EventService;
import com.quizapp.services.LeaderboardService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;
    private final LeaderboardService leaderboardService;

    public EventController(EventService eventService, LeaderboardService leaderboardService) {
        this.eventService = eventService;
        this.leaderboardService = leaderboardService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventResponse createEvent(@RequestBody CreateEventRequest req) {
        return eventService.createEvent(req);
    }

    @PostMapping("/join")
    @ResponseStatus(HttpStatus.OK)
    public EventResponse join(@RequestBody JoinEventRequest req) {
        return eventService.joinEvent(req.joinCode());
    }

    @PostMapping("/start/{eventId}")
    @ResponseStatus(HttpStatus.OK)
    public void startEvent(@PathVariable Long eventId) {
        eventService.startEvent(eventId);
    }

    @GetMapping("/test/{testId}")
    public List<EventResponse> listForTest(@PathVariable Long testId) {
        return eventService.listEventsForTest(testId);
    }

    @GetMapping("/{eventId}")
    public EventResponse getEvent(@PathVariable Long eventId) {
        return eventService.getEventById(eventId);
    }

    @GetMapping()
    public List<EventResponse> listAllEvents() {
        return eventService.listAllEvents();
    }

    @GetMapping("/{eventId}/leaderboard")
    public List<LeaderboardEntry> leaderboard(@PathVariable Long eventId, @RequestParam(defaultValue = "3") int limit) {
        return leaderboardService.topForEvent(eventId, limit);
    }
}
