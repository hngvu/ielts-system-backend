package io.gsp26se16.moni.authentication.dto.response;

import java.time.LocalDate;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserProfileResponse {
    /** credentialId — dùng cho admin endpoints (/users/{id}/*, /credentials/{id}/ban). */
    String id;

    String email;

    String full_name;

    String avatar_url;

    String phoneNumber;

    LocalDate dateOfBirth;

    Double targetReading;
    Double targetListening;
    Double targetWriting;
    Double targetSpeaking;
    Double targetBand;

    LocalDate examDate;

    Double credit;

    String role;
}
