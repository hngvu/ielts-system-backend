package io.gsp26se16.moni.practice.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;

import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.content.entity.Stimulus;
import lombok.*;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Attempt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Integer id;

    LocalDateTime startedAt;
    LocalDateTime submittedAt;
    int score; // correct answers
    int totalQuestions;

    @ManyToOne
    @JoinColumn(name = "test_session_id")
    TestSession testSession;

    @ManyToOne
    @JoinColumn(name = "stimulus_id")
    Stimulus stimulus;

    @ManyToOne
    @JoinColumn(name = "user_id")
    Users user;
}
