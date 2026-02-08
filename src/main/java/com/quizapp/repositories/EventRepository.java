package com.quizapp.repositories;
import com.quizapp.entities.Event;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {
    Optional<Event> findByJoinCode(String joinCode);
}
