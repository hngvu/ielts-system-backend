package io.gsp26se16.moni.content.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import io.gsp26se16.moni.common.enumeration.Skill;
import io.gsp26se16.moni.content.dto.request.TestImportRequest;
import io.gsp26se16.moni.content.dto.request.TestStructureRequest;
import io.gsp26se16.moni.content.dto.request.TestUpdateRequest;
import io.gsp26se16.moni.content.dto.response.TestDetailResponse;
import io.gsp26se16.moni.content.dto.response.TestResponse;

public interface TestService {
    /**
     * Import trọn gói một đề thi mới
     * @param request Dữ liệu JSON cấu trúc cây
     * @return ID của Test vừa tạo
     */
    Integer importTest(TestImportRequest request);

    public Page<TestResponse> getAllTests(String keyword, Skill skill, Integer section, Pageable pageable);

    public Page<TestResponse> getPublishedTests(String keyword, Skill skill, Integer section, Pageable pageable);

    public TestDetailResponse getTestDetail(Integer id);

    public void updateTest(Integer id, TestUpdateRequest request);

    public void deleteTest(Integer id);

    public void addStimulusToTest(Integer testId, TestStructureRequest request);

    public void removeStimulusFromTest(Integer testId, Integer stimulusId);
}
