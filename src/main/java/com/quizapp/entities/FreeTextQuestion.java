package com.quizapp.entities;
import com.quizapp.enums.QuestionType;
import jakarta.persistence.*;

@Entity
@Table(name = "free_text_questions")
public class FreeTextQuestion extends Question {
    @Column(nullable = false, length = 2000)
    private String correctAnswer;

    @Column(nullable = false)
    private boolean caseSensitive = false;

    public FreeTextQuestion() {
        setType(QuestionType.FREE_TEXT);
    }

    public String getCorrectAnswer() {
        return correctAnswer;
    }

    public void setCorrectAnswer(String correctAnswer) {
        this.correctAnswer = correctAnswer;
    }

    public boolean isCaseSensitive() {
        return caseSensitive;
    }

    public void setCaseSensitive(boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }
}
