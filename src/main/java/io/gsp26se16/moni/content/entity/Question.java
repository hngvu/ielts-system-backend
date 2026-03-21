package io.gsp26se16.moni.content.entity;

import java.util.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.persistence.*;

import org.hibernate.annotations.Type;

import io.gsp26se16.moni.common.enumeration.QuestionCategory;
import io.gsp26se16.moni.tag.entity.Tag;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Question {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Integer id;

    String content;
    int position;

    /**
     * Phân loại bản chất câu hỏi: MAIN, FOLLOW_UP, (mở rộng: TRANSITION, OPENING...).
     * Nullable — các câu hỏi Reading/Listening cũ không cần set giá trị này.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "question_category")
    QuestionCategory questionCategory;

    /**
     * Self-reference: câu FOLLOW_UP trỏ về câu MAIN mà nó thuộc về.
     * Câu MAIN thì parentQuestion = null.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_question_id")
    Question parentQuestion;

    @OneToMany(mappedBy = "parentQuestion")
    List<Question> followUpQuestions = new ArrayList<>();

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    Map<String, Object> metadata;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    Map<String, Object> explanation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_group_id")
    QuestionGroup questionGroup;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    List<QuestionOption> options = new ArrayList<>();

    @ManyToMany
    @JoinTable(
            name = "question_tag",
            joinColumns = @JoinColumn(name = "question_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<Tag> tags = new HashSet<>();
}
