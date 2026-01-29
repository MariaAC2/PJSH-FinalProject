package com.quizapp.repositories;

import com.quizapp.entities.EventParticipant;
import com.quizapp.entities.Event;
import com.quizapp.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EventParticipantRepository extends JpaRepository<EventParticipant, Long> {
    Optional<EventParticipant> findByEventAndUser(Event event, User user);
}

