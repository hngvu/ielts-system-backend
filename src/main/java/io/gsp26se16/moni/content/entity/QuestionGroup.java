package io.gsp26se16.moni.content.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class QuestionGroup {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Integer id;
    String instruction;

    @ManyToOne
    @JoinColumn(name = "stimulus_id")
    Stimulus stimulus;

    @ManyToOne
    @JoinColumn(name = "question_type_id")
    QuestionType questionType;
}
