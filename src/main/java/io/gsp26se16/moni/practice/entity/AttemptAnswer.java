package io.gsp26se16.moni.practice.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;

import io.gsp26se16.moni.content.entity.Question;
import io.gsp26se16.moni.content.entity.QuestionOption;
import lombok.*;

@Entity
@Table(name = "attempt_answers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AttemptAnswer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attempt_id")
    private Attempt attempt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id")
    private Question question;

    // Dành cho câu hỏi trắc nghiệm chọn A, B, C, D
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_option_id")
    private QuestionOption selectedOption;

    // Dành cho câu hỏi điền từ (Fill in the blanks)
    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "is_correct")
    private Boolean isCorrect;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "change_count")
    private Integer changeCount;
}
