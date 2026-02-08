package com.quizapp.dtos;

import com.quizapp.entities.Event;
import com.quizapp.entities.EventParticipant;
import com.quizapp.entities.Test;

public record AttemptContext(
        Event event,
        EventParticipant participant,
        Test test
) {}

