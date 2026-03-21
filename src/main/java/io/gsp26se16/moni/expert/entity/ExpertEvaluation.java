package io.gsp26se16.moni.expert.entity;

import java.sql.Timestamp;

import jakarta.persistence.*;

import org.hibernate.annotations.CreationTimestamp;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ExpertEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Integer id;

    @OneToOne
    @JoinColumn(name = "scoring_session_id")
    ScoringSession scoringSession;

    @ManyToOne
    @JoinColumn(name = "expert_profile_id")
    ExpertProfile expertProfile;

    String skill;
    Double overallScore;

    // Speaking criteria
    Double fluency;
    Double vocabulary;
    Double grammar;
    Double pronunciation;

    // Writing criteria
    Double taskResponse;
    Double coherence;
    Double lexicalResource;
    Double grammaticalRange;

    @Column(columnDefinition = "TEXT")
    String feedback;

    @Column(columnDefinition = "TEXT")
    String strengths;

    @Column(columnDefinition = "TEXT")
    String areasForImprovement;

    @CreationTimestamp
    Timestamp createdAt;
}
