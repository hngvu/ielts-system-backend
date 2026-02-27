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
    String email;

    String full_name;

    String avatar_url;

    String phoneNumber;

    LocalDate dateOfBirth;

    Double targetBand;
}
