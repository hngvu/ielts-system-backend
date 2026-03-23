package io.gsp26se16.moni.authentication.entity;

import java.sql.Timestamp;

import jakarta.persistence.*;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
public class UserCredentials {
    public enum Role {
        LEARNER,
        EXPERT,
        ADMIN
    }

    public enum Provider {
        LOCAL,
        GOOGLE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    Users user;

    @Column(nullable = false, unique = true)
    String email;

    String password; // nullable for Google OAuth users

    @Enumerated(EnumType.STRING)
    @Builder.Default
    Provider provider = Provider.LOCAL;

    String googleId;

    @Enumerated(EnumType.STRING)
    @Builder.Default()
    Role role = Role.LEARNER;

    @CreationTimestamp
    Timestamp createdAt;

    @UpdateTimestamp
    Timestamp updatedAt;

    int tokenVersion;

    @Builder.Default
    boolean isActive = true;
}
