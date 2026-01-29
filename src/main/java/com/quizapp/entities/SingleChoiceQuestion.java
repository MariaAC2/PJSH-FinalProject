package com.quizapp.entities;
import com.quizapp.enums.QuestionType;
import jakarta.persistence.*;

@Entity
@Table(name = "single_choice_questions")
public class SingleChoiceQuestion extends ChoiceQuestion {
    public SingleChoiceQuestion() {
        setType(QuestionType.SINGLE_CHOICE);
    }
}