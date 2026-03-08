package io.gsp26se16.moni.content.config;

import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.gsp26se16.moni.common.enumeration.Skill;
import io.gsp26se16.moni.content.entity.QuestionType;
import io.gsp26se16.moni.content.repository.QuestionTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class ContentDataInitializer {

    private final QuestionTypeRepository questionTypeRepository;

    @Bean
    @Order(2) // Chạy sau TagDataInitializer (nếu có)
    @Transactional
    public CommandLineRunner initQuestionTypes() {
        return args -> {
            // Kiểm tra nếu đã có dữ liệu thì thôi
            if (questionTypeRepository.count() > 0) {
                log.info("Question Types already exist. Skipping initialization.");
                return;
            }

            log.info("Initializing Question Types...");

            List<QuestionType> types = List.of(
                    // --- READING ---
                    createType("Multiple Choice", "MCQ", Skill.READING, true), // A,B,C,D chung cho cả nhóm
                    createType("True/False/Not Given", "TFNG", Skill.READING, false), // Mỗi câu 1 drop-down
                    createType("Matching Headings", "MATCHING_HEADINGS", Skill.READING, false),
                    createType("Sentence Completion", "SENTENCE_COMPLETION", Skill.READING, false),

                    // --- LISTENING ---
                    createType("Multiple Choice (Listening)", "MCQ_L", Skill.LISTENING, true),
                    createType("Map Labeling", "MAP_LABELING", Skill.LISTENING, false),
                    createType("Form Completion", "FORM_COMPLETION", Skill.LISTENING, false),

                    // --- WRITING ---
                    createType("Writing Task 1", "WRITING_TASK_1", Skill.WRITING, false),
                    createType("Writing Task 2", "WRITING_TASK_2", Skill.WRITING, false),

                    // --- SPEAKING ---
                    createType("Speaking Part 1", "SPEAKING_PART_1", Skill.SPEAKING, false),
                    createType("Speaking Part 2", "SPEAKING_PART_2", Skill.SPEAKING, false),
                    createType("Speaking Part 3", "SPEAKING_PART_3", Skill.SPEAKING, false));

            questionTypeRepository.saveAll(types);
            log.info("Successfully initialized {} Question Types.", types.size());
        };
    }

    // Helper method
    private QuestionType createType(String name, String code, Skill skill, boolean hasSharedOptions) {
        QuestionType type = new QuestionType();
        type.setName(name);
        type.setCode(code);
        type.setSkill(skill);
        type.setHasSharedOptions(hasSharedOptions);
        return type;
    }
}
