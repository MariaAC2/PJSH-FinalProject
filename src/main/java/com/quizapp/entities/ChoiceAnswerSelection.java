package com.quizapp.entities;
import jakarta.persistence.*;

@Entity
@Table(
        name = "choice_answer_selections",
        uniqueConstraints = @UniqueConstraint(name = "uq_answer_option", columnNames = {"answer_id", "option_id"})
)
public class ChoiceAnswerSelection {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "answer_id", nullable = false)
    private ChoiceAnswer answer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "option_id", nullable = false)
    private Option option;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ChoiceAnswer getAnswer() {
        return answer;
    }

    public void setAnswer(ChoiceAnswer answer) {
        this.answer = answer;
    }

    public Option getOption() {
        return option;
    }

    public void setOption(Option option) {
        this.option = option;
    }
}
