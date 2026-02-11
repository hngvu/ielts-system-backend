package io.gsp26se16.moni.content.service;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {
    /**
     * Upload file lên Storage
     */
    String uploadFile(MultipartFile file);

    /**
     * Xóa file (Dùng khi xóa đề thi hoặc update lại file)
     */
    void deleteFile(String fileUrl);
}
