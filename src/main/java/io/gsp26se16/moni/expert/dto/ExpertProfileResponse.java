package io.gsp26se16.moni.expert.dto;

import java.util.List;

import io.gsp26se16.moni.expert.enumeration.ExpertSpecialization;
import io.gsp26se16.moni.expert.enumeration.ExpertStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ExpertProfileResponse {
    Integer id;
    String displayName;
    String email;
    String avatarUrl;
    Double bandScore;
    Double bandReading;
    Double bandListening;
    Double bandWriting;
    Double bandSpeaking;
    Integer yearsExperience;
    ExpertSpecialization specialization; // kept for backward compat, may be null
    String bio;
    ExpertStatus status;
    Boolean enabled; // Account enabled/disabled
    Double rating;
    Integer totalSessions;
    List<String> certificates;
}
