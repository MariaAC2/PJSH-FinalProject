package com.quizapp.entities;
import com.quizapp.enums.QuestionType;
import jakarta.persistence.*;

@Entity
@Table(name = "multiple_choice_questions")
public class MultipleChoiceQuestion extends ChoiceQuestion {
    public MultipleChoiceQuestion() {
        setType(QuestionType.MULTIPLE_CHOICE);
    }
}
