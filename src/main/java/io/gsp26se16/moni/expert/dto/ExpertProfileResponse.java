package io.gsp26se16.moni.expert.dto;

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
    String avatarUrl;
    Double bandScore;
    Integer yearsExperience;
    ExpertSpecialization specialization;
    String bio;
    ExpertStatus status;
    Double rating;
    Integer totalSessions;
}
