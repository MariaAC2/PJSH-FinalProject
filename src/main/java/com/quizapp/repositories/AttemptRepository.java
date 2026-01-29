package com.quizapp.repositories;

import com.quizapp.entities.Attempt;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AttemptRepository extends JpaRepository<Attempt, Long> {

    @Query("SELECT a FROM Attempt a WHERE a.event.id = :eventId AND a.status = com.quizapp.enums.AttemptStatus.SUBMITTED AND a.participant.user IS NOT NULL ORDER BY a.score DESC, a.submittedAt ASC")
    List<Attempt> findTopSubmittedByEventId(@Param("eventId") Long eventId, Pageable pageable);
}
