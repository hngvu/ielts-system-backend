package io.gsp26se16.moni.vocab.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;

import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;

import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.roadmap.entity.DailySlot;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import lombok.*;

@Entity
@Table(name = "vocab_quiz_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VocabQuizHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slot_id", nullable = false)
    private DailySlot slot;

    @Column(name = "quiz_data", columnDefinition = "jsonb")
    @Type(JsonBinaryType.class)
    private String quizData;

    @Column(name = "score")
    private Integer score;

    @Column(name = "total_questions")
    private Integer totalQuestions;

    @Builder.Default
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
