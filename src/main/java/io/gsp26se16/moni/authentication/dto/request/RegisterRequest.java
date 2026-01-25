package io.gsp26se16.moni.authentication.dto.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.URL;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterRequest {

    @NotBlank(message = "EMAIL_REQUIRED")
    @Email(message = "INVALID_EMAIL_FORMAT")
    private String email;

    @NotBlank(message = "PASSWORD_REQUIRED")
    @Size(min = 8, message = "PASSWORD_MIN_8_CHARS")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).*$", message = "PASSWORD_WEAK")
    private String password;

    @NotBlank(message = "FULL_NAME_REQUIRED")
    @Size(min = 2, max = 100, message = "FULL_NAME_LENGTH_INVALID")
    private String fullName;

    @URL(message = "INVALID_AVATAR_URL")
    private String avatarUrl;

    @Pattern(
            regexp = "^(0?)(3[2-9]|5[6|8|9]|7[0|6-9]|8[0-6|8|9]|9[0-4|6-9])[0-9]{7}$",
            message = "INVALID_PHONE_NUMBER")
    private String phoneNumber;

    @NotNull(message = "DOB_REQUIRED")
    @Past(message = "DOB_MUST_BE_IN_PAST")
    private LocalDate dateOfBirth;
}
