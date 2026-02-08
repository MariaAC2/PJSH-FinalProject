package com.quizapp.entities;
import com.quizapp.enums.EventStatus;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "events", indexes = {
        @Index(name = "ix_events_join_code", columnList = "joinCode", unique = true)
})
public class Event {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Quiz quiz;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User host;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 12, unique = true)
    private String joinCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventStatus status = EventStatus.OPEN;

    private Instant startsAt;
    private Instant endsAt;
    private Instant joinClosesAt;

    @Column(nullable = false)
    private int durationSeconds; // e.g., 600 for 10 minutes

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Quiz getQuiz() {
        return quiz;
    }

    public void setQuiz(Quiz quiz) {
        this.quiz = quiz;
    }

    public User getHost() {
        return host;
    }

    public void setHost(User host) {
        this.host = host;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getJoinCode() {
        return joinCode;
    }

    public void setJoinCode(String joinCode) {
        this.joinCode = joinCode;
    }

    public EventStatus getStatus() {
        return status;
    }

    public void setStatus(EventStatus status) {
        this.status = status;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public void setStartsAt(Instant startsAt) {
        this.startsAt = startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public void setEndsAt(Instant endsAt) {
        this.endsAt = endsAt;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(int durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public Instant getJoinClosesAt() {
        return joinClosesAt;
    }

    public void setJoinClosesAt(Instant joinClosesAt) {
        this.joinClosesAt = joinClosesAt;
    }
}

