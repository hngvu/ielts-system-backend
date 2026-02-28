package io.gsp26se16.moni.content.dto.request;

import io.gsp26se16.moni.common.enumeration.PublishStatus;
import io.gsp26se16.moni.common.enumeration.TestMode;
import io.gsp26se16.moni.common.enumeration.TestType;
import lombok.Data;

import java.util.List;

@Data
public class TestUpdateRequest {
    private String title;
    private String description;
    private Integer duration;  // Thời gian làm bài (nếu cần đổi)
    private TestMode testMode;
    private PublishStatus status;
    // Danh sách Tag ID mới (Nếu Admin muốn update Tag)
    private List<Integer> tagIds;
}
