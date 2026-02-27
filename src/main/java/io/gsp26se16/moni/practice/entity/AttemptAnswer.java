package io.gsp26se16.moni.practice.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;

import io.gsp26se16.moni.content.entity.Question;
import io.gsp26se16.moni.content.entity.QuestionOption;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AttemptAnswer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Integer id;

    boolean isCorrect;
    LocalDateTime createdAt;
    LocalDateTime submittedAt;
    int changeCount;

    @ManyToOne
    @JoinColumn(name = "attempt_id")
    Attempt attempt;

    @ManyToOne
    @JoinColumn(name = "question_id")
    Question question;

    @ManyToOne
    @JoinColumn(name = "selected_option_id")
    QuestionOption questionOption;

    String answerText;
}
