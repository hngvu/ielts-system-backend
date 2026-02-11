package io.gsp26se16.moni.content.service;

import io.gsp26se16.moni.content.dto.request.TestImportRequest;

public interface TestService {
    /**
     * Import trọn gói một đề thi mới
     * @param request Dữ liệu JSON cấu trúc cây
     * @return ID của Test vừa tạo
     */
    Integer importTest(TestImportRequest request);
}
