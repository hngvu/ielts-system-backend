package io.gsp26se16.moni.authentication.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserProfileResponse {
    String email;

    String full_name;

    String avatar_url;

    String phoneNumber;

    LocalDate dateOfBirth;

    Double targetBand;
}
