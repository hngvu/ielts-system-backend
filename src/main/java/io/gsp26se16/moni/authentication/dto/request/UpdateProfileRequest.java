package io.gsp26se16.moni.authentication.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateProfileRequest {
    String fullName;

    String avatarUrl;

    String phoneNumber;

    LocalDate dateOfBirth;
}
