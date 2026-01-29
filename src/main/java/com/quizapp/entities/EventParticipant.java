package com.quizapp.entities;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
        name = "event_participants",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_event_user", columnNames = {"event_id", "user_id"}),
                @UniqueConstraint(name = "uq_event_guest_token", columnNames = {"event_id", "guestToken"})
        }
)
public class EventParticipant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Event event;

    // If you require login, make this non-null and delete guest fields.
    @ManyToOne(fetch = FetchType.LAZY)
    private User user;

    // For guest flow (optional)
    @Column(length = 80)
    private String guestName;

    @Column(length = 64)
    private String guestToken;

    @Column(nullable = false, updatable = false)
    private Instant joinedAt = Instant.now();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Event getEvent() {
        return event;
    }

    public void setEvent(Event event) {
        this.event = event;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getGuestName() {
        return guestName;
    }

    public void setGuestName(String guestName) {
        this.guestName = guestName;
    }

    public String getGuestToken() {
        return guestToken;
    }

    public void setGuestToken(String guestToken) {
        this.guestToken = guestToken;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(Instant joinedAt) {
        this.joinedAt = joinedAt;
    }
}

