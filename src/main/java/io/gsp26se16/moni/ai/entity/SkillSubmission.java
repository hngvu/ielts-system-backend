package io.gsp26se16.moni.ai.entity;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import jakarta.persistence.*;

import org.hibernate.annotations.CreationTimestamp;

import io.gsp26se16.moni.common.enumeration.Skill;
import io.gsp26se16.moni.content.entity.QuestionGroup;
import io.gsp26se16.moni.practice.entity.TestSession;
import lombok.*;

@Entity
@Table(name = "skill_submissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SkillSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "session_id", nullable = false)
    private TestSession testSession;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "question_group_id")
    private QuestionGroup questionGroup;

    @Column(name = "question_id")
    private Long questionId;

    @Column(name = "skill", nullable = false, length = 20)
    private Skill skill;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "word_count")
    private Integer wordCount;

    @Column(name = "audio_url", columnDefinition = "TEXT")
    private String audioUrl;

    @Column(name = "audio_duration")
    private Integer audioDuration;

    @Column(name = "audio_transcript", columnDefinition = "TEXT")
    private String audioTranscript;

    @Column(name = "time_spent")
    private Timestamp timeSpent;

    @Column(name = "evaluation_status", length = 20)
    private String evaluationStatus;

    @CreationTimestamp
    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;
}
