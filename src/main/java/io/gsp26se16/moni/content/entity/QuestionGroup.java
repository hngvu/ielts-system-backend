package io.gsp26se16.moni.content.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.persistence.*;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    @Column(columnDefinition = "TEXT")
    String groupContent; // gap-fill template with {{1}}, {{2}} placeholders

    String imageUrl; // diagram label image URL

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    List<Map<String, Object>> sharedOptions; // [{label: "A", content: "River Cam"}, ...]

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stimulus_id")
    Stimulus stimulus;

    @ManyToOne
    @JoinColumn(name = "question_type_id")
    QuestionType questionType;

    @OneToMany(mappedBy = "questionGroup", cascade = CascadeType.ALL, orphanRemoval = true)
    List<Question> questions = new ArrayList<>();
}
