package io.gsp26se16.moni.expert.dto;

import java.util.List;

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
    Double bandReading;
    Double bandListening;
    Double bandWriting;
    Double bandSpeaking;
    Integer yearsExperience;
    String bio;
    List<String> certificates;
}
