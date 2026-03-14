package io.gsp26se16.moni.roadmap.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;

import lombok.*;

@Entity
@Table(name = "roadmaps")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Roadmap {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goal_id")
    private Goal goal;

    private Integer version;

    private String status;

    private Integer priority;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
