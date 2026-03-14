package io.gsp26se16.moni.roadmap.entity;

import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.common.enumeration.Skill;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Entity
@Table(name = "goal")
@AllArgsConstructor
@NoArgsConstructor
public class Goal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private Users user;

    @Column(name = "starting_band")
    private Double startingBand;

    @Column(name = "target_band")
    private Double targetBand;

    private LocalDate deadline;

    @Enumerated(EnumType.STRING)
    private Skill skill;

    private String status;
}
