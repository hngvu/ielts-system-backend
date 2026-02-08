package io.gsp26se16.moni.practice.entity;

import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.content.entity.Test;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TestSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Integer id;

    LocalDateTime startedAt;
    LocalDateTime endedAt;
    double bandScore;

    @ManyToOne
    @JoinColumn(name = "test_id")
    Test test;

    @ManyToOne
    @JoinColumn(name = "user_id")
    Users user;
}
