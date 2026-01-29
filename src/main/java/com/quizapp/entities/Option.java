package com.quizapp.entities;
import jakarta.persistence.*;

@Entity
@Table(
        name = "question_options",
        uniqueConstraints = @UniqueConstraint(name = "uq_option_question_position",
                columnNames = {"question_id", "position"})
)
public class Option {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private ChoiceQuestion question;

    @Column(nullable = false, length = 500)
    private String optionText;

    @Column(nullable = false)
    private boolean correct;

    @Column(nullable = false)
    private int position;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ChoiceQuestion getQuestion() {
        return question;
    }

    public void setQuestion(ChoiceQuestion question) {
        this.question = question;
    }

    public String getOptionText() {
        return optionText;
    }

    public void setOptionText(String optionText) {
        this.optionText = optionText;
    }

    public boolean isCorrect() {
        return correct;
    }

    public void setCorrect(boolean correct) {
        this.correct = correct;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }
}
