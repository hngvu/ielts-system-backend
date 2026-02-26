package io.gsp26se16.moni.tag.config;

import java.util.ArrayList;
import java.util.List;

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
        // Kiểm tra xem bảng tags đã có dữ liệu chưa
        if (tagRepository.count() > 0) {
            log.info("Tags data already exists. Skipping initialization.");
            return;
        }

        log.info("Initializing Tags data...");
        List<Tag> tags = new ArrayList<>();

        // 1. Tạo SKILL Tags (4 Kỹ năng)
        tags.add(createTag("Listening", "SKILL_LISTENING", TagType.SKILL, "Kỹ năng Nghe IELTS"));
        tags.add(createTag("Reading", "SKILL_READING", TagType.SKILL, "Kỹ năng Đọc IELTS"));
        tags.add(createTag("Writing", "SKILL_WRITING", TagType.SKILL, "Kỹ năng Viết IELTS"));
        tags.add(createTag("Speaking", "SKILL_SPEAKING", TagType.SKILL, "Kỹ năng Nói IELTS"));

        // 2. Tạo DIFFICULTY Tags (Độ khó)
        tags.add(createTag("Easy", "DIFF_EASY", TagType.DIFFICULTY, "Dành cho người mới bắt đầu (Band 0-4.0)"));
        tags.add(createTag("Medium", "DIFF_MEDIUM", TagType.DIFFICULTY, "Trung bình (Band 4.5-6.0)"));
        tags.add(createTag("Hard", "DIFF_HARD", TagType.DIFFICULTY, "Nâng cao (Band 6.5+)"));

        // 3. Tạo QUESTION TYPE Tags (Dạng bài phổ biến)
        // Reading & Listening
        tags.add(createTag("Multiple Choice", "QT_MCQ", TagType.QUESTION_TYPE, "Trắc nghiệm nhiều lựa chọn"));
        tags.add(createTag("Matching Headings", "QT_MATCH_HEAD", TagType.QUESTION_TYPE, "Nối tiêu đề đoạn văn"));
        tags.add(createTag("True/False/Not Given", "QT_TFNG", TagType.QUESTION_TYPE, "Xác định thông tin đúng sai"));
        tags.add(createTag("Map Labeling", "QT_MAP", TagType.QUESTION_TYPE, "Dán nhãn bản đồ"));
        tags.add(createTag("Sentence Completion", "QT_SENTENCE_COMP", TagType.QUESTION_TYPE, "Hoàn thành câu"));
        // Writing
        tags.add(createTag("Task 1: Line Graph", "QT_W_TASK1_LINE", TagType.QUESTION_TYPE, "Biểu đồ đường"));
        tags.add(createTag("Task 2: Essay", "QT_W_TASK2_ESSAY", TagType.QUESTION_TYPE, "Bài luận xã hội"));

        // 4. Tạo TOPIC Tags (Chủ đề thường gặp)
        tags.add(createTag("Environment", "TOPIC_ENV", TagType.TOPIC, "Môi trường, Khí hậu, Tái chế"));
        tags.add(createTag("Education", "TOPIC_EDU", TagType.TOPIC, "Giáo dục, Trường học"));
        tags.add(createTag("Technology", "TOPIC_TECH", TagType.TOPIC, "Công nghệ, AI, Internet"));
        tags.add(createTag("Health", "TOPIC_HEALTH", TagType.TOPIC, "Sức khỏe, Y tế"));
        tags.add(createTag("Travel", "TOPIC_TRAVEL", TagType.TOPIC, "Du lịch, Văn hóa"));

        tagRepository.saveAll(tags);
        log.info("Successfully initialized {} tags into Database.", tags.size());
    }

    // Helper method để code gọn hơn
    private Tag createTag(String name, String code, TagType type, String description) {
        return Tag.builder()
                .name(name)
                .code(code)
                .type(type)
                .description(description)
                .build();
    }
}
