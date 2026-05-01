package io.gsp26se16.moni.authentication.dto.request;

import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

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

    // IELTS target scores (0–9, step 0.5)
    @DecimalMin(value = "0.0", message = "Điểm IELTS phải từ 0.0")
    @DecimalMax(value = "9.0", message = "Điểm IELTS tối đa 9.0")
    Double targetReading;

    @DecimalMin(value = "0.0", message = "Điểm IELTS phải từ 0.0")
    @DecimalMax(value = "9.0", message = "Điểm IELTS tối đa 9.0")
    Double targetListening;

    @DecimalMin(value = "0.0", message = "Điểm IELTS phải từ 0.0")
    @DecimalMax(value = "9.0", message = "Điểm IELTS tối đa 9.0")
    Double targetWriting;

    @DecimalMin(value = "0.0", message = "Điểm IELTS phải từ 0.0")
    @DecimalMax(value = "9.0", message = "Điểm IELTS tối đa 9.0")
    Double targetSpeaking;

    @DecimalMin(value = "0.0", message = "Điểm IELTS phải từ 0.0")
    @DecimalMax(value = "9.0", message = "Điểm IELTS tối đa 9.0")
    Double targetBand;

    LocalDate examDate;
}
