package io.gsp26se16.moni.content.dto.request;

import java.util.List;

import io.gsp26se16.moni.common.enumeration.PublishStatus;
import io.gsp26se16.moni.common.enumeration.Skill;
import io.gsp26se16.moni.common.enumeration.TestMode;
import lombok.Data;

@Data
public class TestUpdateRequest {
    private String title;
    private String description;
    private String thumbnailUrl;
    private Integer duration;
    private Skill skill;
    private TestMode testMode;
    private Integer section;
    private PublishStatus status;
    private List<Integer> tagIds;
}
