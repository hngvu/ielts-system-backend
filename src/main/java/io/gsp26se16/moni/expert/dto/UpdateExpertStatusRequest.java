package io.gsp26se16.moni.expert.dto;

import io.gsp26se16.moni.expert.enumeration.ExpertStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateExpertStatusRequest {
    ExpertStatus status;
}
