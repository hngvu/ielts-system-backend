package io.gsp26se16.moni.content.dto.request;

import java.util.List;

import io.gsp26se16.moni.common.enumeration.TestType;
import lombok.Data;

@Data
public class TestUpdateRequest {
    private String title;
    private String description;
    private TestType testType; // VD: Đổi từ PRACTICE sang FULL_TEST
    private Integer duration; // Thời gian làm bài (nếu cần đổi)

    // Danh sách Tag ID mới (Nếu Admin muốn update Tag)
    private List<Integer> tagIds;
}
