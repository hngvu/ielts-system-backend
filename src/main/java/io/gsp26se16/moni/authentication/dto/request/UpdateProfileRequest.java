package io.gsp26se16.moni.authentication.dto.request;

import java.time.LocalDate;

import lombok.*;
import lombok.experimental.FieldDefaults;

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
