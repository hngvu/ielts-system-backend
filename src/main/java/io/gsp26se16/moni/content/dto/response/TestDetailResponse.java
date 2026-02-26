package io.gsp26se16.moni.content.dto.response;

import java.util.List;
import java.util.Map;

import io.gsp26se16.moni.common.enumeration.Skill;
import io.gsp26se16.moni.common.enumeration.TestType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TestDetailResponse {
    private Integer id;
    private String title;
    private String description;
    private Skill skill;
    private TestType testType;
    private Integer duration;
    private List<String> tags; // Danh sách Tag

    // Danh sách các phần thi (Stimulus)
    private List<StimulusDetail> stimuli;

    // --- INNER CLASSES (Dùng để hứng dữ liệu con) ---

    @Data
    @Builder
    public static class StimulusDetail {
        private Integer id;
        private String title;
        private String content; // Nội dung bài đọc
        private String mediaUrl; // Audio/Ảnh
        private Integer section; // Thứ tự (Part 1, 2...)

        private List<QuestionGroupDetail> questionGroups;
    }

    @Data
    @Builder
    public static class QuestionGroupDetail {
        private Integer id;
        private String instruction;
        private String questionTypeCode; // Mã loại câu hỏi (MCQ, TFNG...)

        private List<QuestionDetail> questions;
    }

    @Data
    @Builder
    public static class QuestionDetail {
        private Integer id;
        private String content;
        private Integer position;
        private Map<String, Object> metadata;
        private Map<String, Object> explanation; // Admin được xem giải thích

        private List<OptionDetail> options;
    }

    @Data
    @Builder
    public static class OptionDetail {
        private Integer id;
        private String label; // A, B, C, D
        private String content;
        private Boolean isCorrect; // Admin được xem đáp án đúng
    }
}
