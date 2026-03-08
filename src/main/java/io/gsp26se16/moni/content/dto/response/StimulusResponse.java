package io.gsp26se16.moni.content.dto.response;

import io.gsp26se16.moni.common.enumeration.PublishStatus;
import io.gsp26se16.moni.common.enumeration.Skill;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StimulusResponse {
    private Integer id;
    private String title;
    private Skill skill;
    private PublishStatus status;
}
