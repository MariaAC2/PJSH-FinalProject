package com.quizapp.entities;
import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "choice_answers")
public class ChoiceAnswer extends Answer {

    @OneToMany(mappedBy = "answer", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ChoiceAnswerSelection> selections = new HashSet<>();

    public Set<ChoiceAnswerSelection> getSelections() {
        return selections;
    }

    public void setSelections(Set<ChoiceAnswerSelection> selections) {
        this.selections = selections;
    }
}
