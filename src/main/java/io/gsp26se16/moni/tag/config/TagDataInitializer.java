package io.gsp26se16.moni.tag.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.gsp26se16.moni.tag.entity.Tag;
import io.gsp26se16.moni.tag.entity.TagType;
import io.gsp26se16.moni.tag.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class TagDataInitializer implements CommandLineRunner {

    private final TagRepository tagRepository;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        log.info("Initializing Tags data if not present...");

        // 1. Tạo SKILL Tags (4 Kỹ năng)
        createTagIfNotExists("Listening", "SKILL_LISTENING", TagType.SKILL, "Kỹ năng Nghe IELTS");
        createTagIfNotExists("Reading", "SKILL_READING", TagType.SKILL, "Kỹ năng Đọc IELTS");
        createTagIfNotExists("Writing", "SKILL_WRITING", TagType.SKILL, "Kỹ năng Viết IELTS");
        createTagIfNotExists("Speaking", "SKILL_SPEAKING", TagType.SKILL, "Kỹ năng Nói IELTS");

        // 2. Tạo DIFFICULTY Tags (Độ khó)
        createTagIfNotExists("Easy", "DIFF_EASY", TagType.DIFFICULTY, "Dành cho người mới bắt đầu (Band 0-4.0)");
        createTagIfNotExists("Medium", "DIFF_MEDIUM", TagType.DIFFICULTY, "Trung bình (Band 4.5-6.0)");
        createTagIfNotExists("Hard", "DIFF_HARD", TagType.DIFFICULTY, "Nâng cao (Band 6.5+)");

        // 3. Tạo QUESTION TYPE Tags (Dạng bài phổ biến mapped từ QuestionTypeCode)
        // Cần phải có tag code chuẩn "QT_" + QuestionTypeCode
        createTagIfNotExists("Multiple Choice", "QT_MCQ", TagType.QUESTION_TYPE, "Trắc nghiệm 1 lựa chọn");
        createTagIfNotExists(
                "Multiple Choice (Multiple Answers)",
                "QT_MCQ_MULTIPLE",
                TagType.QUESTION_TYPE,
                "Trắc nghiệm nhiều lựa chọn");
        createTagIfNotExists(
                "True/False/Not Given", "QT_TFNG", TagType.QUESTION_TYPE, "Xác định thông tin đúng/sai/không đề cập");
        createTagIfNotExists(
                "Yes/No/Not Given", "QT_YNNG", TagType.QUESTION_TYPE, "Xác định quan điểm đúng/sai/không có");
        createTagIfNotExists(
                "Matching Headings", "QT_MATCHING_HEADINGS", TagType.QUESTION_TYPE, "Nối tiêu đề đoạn văn");
        createTagIfNotExists(
                "Matching Information", "QT_MATCHING_INFORMATION", TagType.QUESTION_TYPE, "Nối thông tin vào đoạn văn");
        createTagIfNotExists(
                "Matching Features", "QT_MATCHING_FEATURE", TagType.QUESTION_TYPE, "Nối đặc điểm nhân vật");
        createTagIfNotExists(
                "Diagram/Map Labeling", "QT_DIAGRAM_LABEL", TagType.QUESTION_TYPE, "Dán nhãn sơ đồ/bản đồ");
        createTagIfNotExists("Gap Filling", "QT_GAP_FILLING", TagType.QUESTION_TYPE, "Điền từ vào chỗ trống");
        createTagIfNotExists("Short Answer", "QT_SHORT_ANSWER", TagType.QUESTION_TYPE, "Trả lời câu hỏi ngắn");

        // 4. Tạo TOPIC Tags (Chủ đề thường gặp)
        createTagIfNotExists("Environment", "TOPIC_ENV", TagType.TOPIC, "Môi trường, Khí hậu, Tái chế");
        createTagIfNotExists("Education", "TOPIC_EDU", TagType.TOPIC, "Giáo dục, Trường học");
        createTagIfNotExists("Technology", "TOPIC_TECH", TagType.TOPIC, "Công nghệ, AI, Internet");
        createTagIfNotExists("Health", "TOPIC_HEALTH", TagType.TOPIC, "Sức khỏe, Y tế");
        createTagIfNotExists("Travel", "TOPIC_TRAVEL", TagType.TOPIC, "Du lịch, Văn hóa");

        // 5. Tạo WRITING_TYPE Tags (Các dạng bài Writing cụ thể)
        createTagIfNotExists(
                "Advantages and Disadvantages", "WT_ADV_DISADV", TagType.WRITING_TYPE, "Task 2 - Lợi ích và Tác hại");
        createTagIfNotExists(
                "Agree or Disagree", "WT_AGREE_DISAGREE", TagType.WRITING_TYPE, "Task 2 - Đồng ý hay Không đồng ý");
        createTagIfNotExists(
                "Causes, Problems and Solutions",
                "WT_CAUSES_SOLUTIONS",
                TagType.WRITING_TYPE,
                "Task 2 - Nguyên nhân và Giải pháp");
        createTagIfNotExists("Discussion", "WT_DISCUSSION", TagType.WRITING_TYPE, "Task 2 - Thảo luận 2 quan điểm");
        createTagIfNotExists(
                "Positive or Negative Development",
                "WT_POS_NEG",
                TagType.WRITING_TYPE,
                "Task 2 - Sự phát triển tích cực hay tiêu cực");
        createTagIfNotExists("Two-Part Question", "WT_TWO_PART", TagType.WRITING_TYPE, "Task 2 - Câu hỏi gồm 2 phần");

        createTagIfNotExists("Line graph", "WT_LINE", TagType.WRITING_TYPE, "Biểu đồ đường - Task 1");
        createTagIfNotExists("Bar chart", "WT_BAR", TagType.WRITING_TYPE, "Biểu đồ cột - Task 1");
        createTagIfNotExists("Pie chart", "WT_PIE", TagType.WRITING_TYPE, "Biểu đồ tròn - Task 1");
        createTagIfNotExists("Table", "WT_TABLE", TagType.WRITING_TYPE, "Bảng biểu - Task 1");
        createTagIfNotExists("Map", "WT_MAP", TagType.WRITING_TYPE, "Bản đồ - Task 1");
        createTagIfNotExists("Process", "WT_PROCESS", TagType.WRITING_TYPE, "Quy trình - Task 1");
        createTagIfNotExists("Multiple charts", "WT_MULTI", TagType.WRITING_TYPE, "Biểu đồ kết hợp - Task 1");
        createTagIfNotExists("Task 2: Essay", "WT_ESSAY", TagType.WRITING_TYPE, "Bài luận xã hội - Task 2");

        // 6. Tạo SPEAKING CRITERIA Tags
        createTagIfNotExists("Fluency and Coherence", "FC", TagType.SPEAKING_CRITERIA, "Fluency and Coherence");
        createTagIfNotExists("Lexical Resource", "LR", TagType.SPEAKING_CRITERIA, "Lexical Resource");
        createTagIfNotExists(
                "Grammatical Range and Accuracy", "GRA", TagType.SPEAKING_CRITERIA, "Grammatical Range and Accuracy");
        createTagIfNotExists("Pronunciation", "PR", TagType.SPEAKING_CRITERIA, "Pronunciation");

        // 7. Tạo WRITING CRITERIA Tags
        createTagIfNotExists("Task Achievement", "TA", TagType.WRITING_CRITERIA, "Task Achievement / Task Response");
        createTagIfNotExists("Coherence and Cohesion", "CC", TagType.WRITING_CRITERIA, "Coherence and Cohesion");
        createTagIfNotExists("Lexical Resource", "LR", TagType.WRITING_CRITERIA, "Lexical Resource");
        createTagIfNotExists(
                "Grammatical Range and Accuracy", "GRA", TagType.WRITING_CRITERIA, "Grammatical Range and Accuracy");

        log.info("Tags data initialization complete.");
    }

    private void createTagIfNotExists(String name, String code, TagType type, String description) {
        if (!tagRepository.existsByCode(code)) {
            Tag tag = Tag.builder()
                    .name(name)
                    .code(code)
                    .type(type)
                    .description(description)
                    .build();
            tagRepository.save(tag);
        }
    }
}
