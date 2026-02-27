package io.gsp26se16.moni.content.service;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CloudinaryStorageService implements StorageService {

    private final Cloudinary cloudinary;

    @Override
    public String uploadFile(MultipartFile file) {
        try {
            // Tạo tên file ngẫu nhiên để không bị trùng trên Cloud
            String fileName = UUID.randomUUID().toString();

            Map params = ObjectUtils.asMap(
                    "public_id", fileName,
                    "folder", "ielts-content",
                    "resource_type", "auto");

            Map uploadResult = cloudinary.uploader().upload(file.getBytes(), params);

            // Lấy về đường dẫn HTTPS an toàn
            return (String) uploadResult.get("secure_url");

        } catch (IOException e) {
            log.error("Cloudinary upload failed", e);
            throw new RuntimeException("Failed to upload file to Cloudinary");
        }
    }

    @Override
    public void deleteFile(String fileUrl) {
        if (fileUrl == null || fileUrl.isEmpty()) return;

        try {
            String publicId = getPublicIdFromUrl(fileUrl);
            String resourceType = getResourceType(fileUrl); // Xác định là image hay video (audio)

            // Gọi API xóa của Cloudinary
            cloudinary
                    .uploader()
                    .destroy(
                            publicId,
                            ObjectUtils.asMap(
                                    "resource_type",
                                    resourceType,
                                    "invalidate",
                                    true // Xóa cả cache trên CDN để user không nhìn thấy ảnh cũ
                                    ));

            log.info("Deleted file on Cloudinary: {}", publicId);
        } catch (Exception e) {
            log.error("Failed to delete file on Cloudinary: {}", fileUrl, e);
            // Không throw exception để tránh làm gián đoạn luồng chính (ví dụ xóa đề thi)
        }
    }

    // Helper: Trích xuất Public ID từ URL
    private String getPublicIdFromUrl(String url) {
        // Regex pattern: Tìm chuỗi sau "upload/" (và bỏ qua version v123...) cho đến trước dấu chấm đuôi file
        // VD: .../upload/v12345/ielts-content/abc.jpg -> ielts-content/abc
        Pattern pattern = Pattern.compile("upload/(?:v\\d+/)?([^.]+)");
        Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new IllegalArgumentException("Invalid Cloudinary URL");
    }

    // Helper: Xác định loại resource (image hoặc video) dựa vào đuôi file
    private String getResourceType(String url) {
        if (url.endsWith(".mp3") || url.endsWith(".wav") || url.endsWith(".mp4")) {
            return "video"; // Cloudinary coi Audio là Video
        }
        return "image";
    }
}
