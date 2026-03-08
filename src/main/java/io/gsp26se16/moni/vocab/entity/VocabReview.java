package io.gsp26se16.moni.vocab.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;

import io.gsp26se16.moni.authentication.entity.Users;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class VocabReview {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Integer id;

    LocalDateTime reviewedAt;

    @OneToOne
    @JoinColumn(name = "vocab_id")
    Vocab vocab;

    @ManyToOne
    @JoinColumn(name = "user_id")
    Users user;
}
