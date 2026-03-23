package io.gsp26se16.moni.expert.dto;

import io.gsp26se16.moni.expert.enumeration.ExpertSpecialization;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateExpertRequest {
    String email;
    String password;
    String displayName;
    String avatarUrl;
    Double bandScore; // overall
    Double bandReading;
    Double bandListening;
    Double bandWriting;
    Double bandSpeaking;
    Integer yearsExperience;
    ExpertSpecialization specialization;
    String bio;
}
