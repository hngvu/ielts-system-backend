package io.gsp26se16.moni.content.dto.request;

import java.util.List;

import io.gsp26se16.moni.common.enumeration.PublishStatus;
import io.gsp26se16.moni.common.enumeration.TestMode;
import lombok.Data;

@Data
public class TestUpdateRequest {
    private String title;
    private String description;
    private Integer duration; // Thời gian làm bài (nếu cần đổi)
    private TestMode testMode;
    private PublishStatus status;
    // Danh sách Tag ID mới (Nếu Admin muốn update Tag)
    private List<Integer> tagIds;
}
