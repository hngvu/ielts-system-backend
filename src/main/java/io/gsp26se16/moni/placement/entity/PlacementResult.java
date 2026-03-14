package io.gsp26se16.moni.placement.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;

import org.hibernate.annotations.CreationTimestamp;

import io.gsp26se16.moni.authentication.entity.Users;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "placement_result")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PlacementResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    Users user;

    Double readingBand;
    Double listeningBand;
    Double writingBand;
    Double speakingBand;
    Double overallBand;
    Double targetBand;

    Integer readingCorrect;
    Integer listeningCorrect;

    Boolean isSelfAssessed;

    @CreationTimestamp
    LocalDateTime completedAt;
}
