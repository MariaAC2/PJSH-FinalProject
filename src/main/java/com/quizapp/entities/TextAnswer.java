package com.quizapp.entities;
import jakarta.persistence.*;

@Entity
@Table(name = "text_answers")
public class TextAnswer extends Answer {

    @Column(length = 4000)
    private String answerText;

    public String getAnswerText() {
        return answerText;
    }

    public void setAnswerText(String answerText) {
        this.answerText = answerText;
    }
}
