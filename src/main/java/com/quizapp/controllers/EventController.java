package com.quizapp.controllers;

import com.quizapp.dtos.CreateEventRequest;
import com.quizapp.dtos.EventResponse;
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
    public ResponseEntity<EventResponse> createEvent(@RequestBody CreateEventRequest req) {
        EventResponse resp = eventService.createEvent(req);
        return ResponseEntity.created(URI.create("/api/events/" + resp.id())).body(resp);
    }

    @GetMapping("/test/{testId}")
    public List<EventResponse> listForTest(@PathVariable Long testId) {
        return eventService.listEventsForTest(testId);
    }

    @GetMapping("/{eventId}/leaderboard")
    public List<LeaderboardEntry> leaderboard(@PathVariable Long eventId, @RequestParam(defaultValue = "3") int limit) {
        return leaderboardService.topForEvent(eventId, limit);
    }
}
