package io.gsp26se16.moni.roadmap.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "roadmap")
@AllArgsConstructor
@NoArgsConstructor
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
