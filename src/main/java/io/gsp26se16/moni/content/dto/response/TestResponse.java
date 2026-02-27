package io.gsp26se16.moni.content.dto.response;

import io.gsp26se16.moni.common.enumeration.PublishStatus;
import io.gsp26se16.moni.common.enumeration.Skill;
import io.gsp26se16.moni.common.enumeration.TestMode;
import io.gsp26se16.moni.common.enumeration.TestType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class TestResponse {
    private Integer id;
    private String title;
    private Skill skill;
    private TestType testType;
    private Integer duration;
    private TestMode testMode;
    private PublishStatus status;
    private List<Integer> tagIds;
}
