package io.gsp26se16.moni.roadmap.entity;

import jakarta.persistence.*;

import io.gsp26se16.moni.content.entity.Stimulus;
import io.gsp26se16.moni.content.entity.Test;
import lombok.*;

@Entity
@Table(name = "tasks")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Task {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "roadmap_id")
    private Roadmap roadmap;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_id")
    private Test test;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stimulus_id")
    private Stimulus stimulus;

    @Column(name = "\"order\"")
    private Integer order;

    @Column(name = "task_type")
    private String taskType;

    private String status;
}
