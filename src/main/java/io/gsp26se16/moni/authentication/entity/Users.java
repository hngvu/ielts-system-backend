package io.gsp26se16.moni.authentication.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.sql.Timestamp;
import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
public class Users {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    String full_name;

    String avatar_url;

    String phoneNumber;

    LocalDate dateOfBirth;

    @OneToOne(mappedBy = "user")
    UserCredentials credential;

    @CreationTimestamp
    Timestamp createdAt;

    @UpdateTimestamp
    Timestamp updatedAt;
}
