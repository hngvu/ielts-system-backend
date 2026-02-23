package io.gsp26se16.moni.content.dto.response;

import io.gsp26se16.moni.common.enumeration.Skill;
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
    private String description;
    private Skill skill;       // READING, LISTENING...
    private TestType testType; // FULL_TEST, PRACTICE...
    private Integer duration;  // Thời gian làm bài (phút)

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Hiển thị danh sách tên Tag (VD: ["Cambridge", "Hard"])
    private List<String> tags;
}
