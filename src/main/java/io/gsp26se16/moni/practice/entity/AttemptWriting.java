package io.gsp26se16.moni.practice.entity;

import io.gsp26se16.moni.content.entity.Question;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "attempt_writing")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AttemptWriting {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attempt_id")
    private Attempt attempt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id")
    private Question question;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "word_count")
    private Integer wordCount;
}
