package io.gsp26se16.moni.content.dto.request;

import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import io.gsp26se16.moni.common.enumeration.QuestionCategory;
import io.gsp26se16.moni.common.enumeration.Skill;
import io.gsp26se16.moni.common.enumeration.TestMode;
import io.gsp26se16.moni.common.enumeration.TestType;
import lombok.Data;

@Data
public class TestImportRequest {

    @NotBlank(message = "Title is required")
    private String title; // Ví dụ: "Cambridge 18 - Test 1"

    private String description;

    @NotNull(message = "Skill is required")
    private Skill skill; // READING, LISTENING, WRITING, SPEAKING

    private TestType testType; // FULL_TEST, PRACTICE (optional, defaults handled by service)

    private Integer duration;
    private TestMode testMode;
    private Integer section; // Passage/Section/Task/Part number

    private String thumbnailUrl;

    private List<Integer> tagIds; // Danh sách ID của Tag (VD: [1, 5, 8])
    // Danh sách các bài đọc/nghe (Stimulus) trong đề này
    private List<StimulusRequest> stimuli;

    // --- CÁC CLASS CON (INNER CLASS) ---

    @Data
    public static class StimulusRequest {
        @NotBlank
        private String title; // Tên bài đọc (VD: "Passage 1: The History of Tea")

        private String content; // Nội dung bài đọc (HTML/Text)
        private String mediaUrl; // Link Audio (nếu là Listening)
        private Integer section; // Thứ tự (1, 2, 3)

        // Danh sách nhóm câu hỏi trong bài đọc này
        private List<QuestionGroupRequest> questionGroups;
    }

    @Data
    public static class QuestionGroupRequest {
        private String instruction; // Hướng dẫn (VD: "Choose the correct letter...")

        @NotBlank(message = "Question Type Code is required")
        private String questionTypeCode; // Quan trọng: Phải khớp với mã trong DB (VD: "MCQ", "TFNG")

        private String groupContent; // Gap-fill template with {{1}}, {{2}} placeholders
        private String imageUrl; // Diagram label image URL
        private List<Map<String, Object>> sharedOptions; // [{label: "A", content: "River Cam"}, ...]

        private List<QuestionRequest> questions;
    }

    @Data
    public static class QuestionRequest {
        private String content; // Nội dung câu hỏi
        private Integer position; // Số thứ tự câu (VD: 1, 2, 3...)

        /** MAIN, FOLLOW_UP, (mở rộng: TRANSITION, OPENING...). Null = câu hỏi Reading/Listening bình thường */
        private QuestionCategory questionCategory;

        /** Position của câu MAIN mà follow-up này thuộc về. Null = câu MAIN hoặc câu không phải Speaking */
        private Integer parentQuestionPosition;

        private Map<String, Object> metadata; // Dữ liệu phụ (JSONB)
        private Map<String, Object> explanation; // Giải thích chi tiết (JSONB)
        private List<Integer> tagIds;
        private List<OptionRequest> options;
    }

    @Data
    public static class OptionRequest {
        private String label; // A, B, C, D
        private String content; // Nội dung đáp án
        private Boolean isCorrect; // Đáp án đúng hay sai (Admin được quyền gửi cái này)
    }
}
