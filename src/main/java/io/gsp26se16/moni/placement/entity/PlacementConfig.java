package io.gsp26se16.moni.placement.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import io.gsp26se16.moni.content.entity.Test;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "placement_config")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PlacementConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Integer id;

    String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reading_test_id", nullable = false)
    Test readingTest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "listening_test_id", nullable = false)
    Test listeningTest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "writing_test_id", nullable = false)
    Test writingTest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "speaking_test_id", nullable = false)
    Test speakingTest;

    @Builder.Default
    Boolean isActive = false;

    @CreationTimestamp
    LocalDateTime createdAt;

    @UpdateTimestamp
    LocalDateTime updatedAt;
}
