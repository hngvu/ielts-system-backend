package io.gsp26se16.moni.content.config;

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
            log.info("Upserting Question Types...");
            int created = 0;

            // --- READING ---
            created += upsertType("Multiple Choice", "MCQ", Skill.READING, false);
            created += upsertType("Multiple Choice (Multiple Answers)", "MCQ_MULTIPLE", Skill.READING, false);
            created += upsertType("True/False/Not Given", "TFNG", Skill.READING, false);
            created += upsertType("Yes/No/Not Given", "YNNG", Skill.READING, false);
            created += upsertType("Matching Headings", "MATCHING_HEADINGS", Skill.READING, true);
            created += upsertType("Matching Information", "MATCHING_INFORMATION", Skill.READING, true);
            created += upsertType("Matching Feature", "MATCHING_FEATURE", Skill.READING, true);
            created += upsertType("Sentence Completion", "SENTENCE_COMPLETION", Skill.READING, false);
            created += upsertType("Fill in the Blank", "FILL_IN_THE_BLANK", Skill.READING, false);
            created += upsertType("Short Answer", "SHORT_ANSWER", Skill.READING, false);
            created += upsertType("Gap Filling", "GAP_FILLING", Skill.READING, false);
            created += upsertType("Diagram Label", "DIAGRAM_LABEL", Skill.READING, false);
            created += upsertType("Matching", "MATCHING", Skill.READING, true);

            // --- LISTENING ---
            created += upsertType("Multiple Choice (Listening)", "MCQ_L", Skill.LISTENING, false);
            created += upsertType("Map Labeling", "MAP_LABELING", Skill.LISTENING, false);
            created += upsertType("Form Completion", "FORM_COMPLETION", Skill.LISTENING, false);
            created += upsertType("Note Completion", "NOTE_COMPLETION", Skill.LISTENING, false);
            created += upsertType("Table Completion", "TABLE_COMPLETION", Skill.LISTENING, false);
            created += upsertType("Gap Filling (Listening)", "GAP_FILLING_L", Skill.LISTENING, false);

            // --- WRITING ---
            created += upsertType("Writing Task 1", "WRITING_TASK_1", Skill.WRITING, false);
            created += upsertType("Writing Task 2", "WRITING_TASK_2", Skill.WRITING, false);
            // Writing Task 1 sub-types
            created += upsertType("Line Graph", "LINE_GRAPH", Skill.WRITING, false);
            created += upsertType("Bar Chart", "BAR_CHART", Skill.WRITING, false);
            created += upsertType("Pie Chart", "PIE_CHART", Skill.WRITING, false);
            created += upsertType("Table", "TABLE", Skill.WRITING, false);
            created += upsertType("Mixed Graph", "MIXED_GRAPH", Skill.WRITING, false);
            created += upsertType("Map", "MAP", Skill.WRITING, false);
            created += upsertType("Process", "PROCESS", Skill.WRITING, false);
            // Writing Task 2 sub-types
            created += upsertType("Agree or Disagree", "AGREE_DISAGREE", Skill.WRITING, false);
            created += upsertType("Discussion", "DISCUSSION", Skill.WRITING, false);
            created += upsertType("Advantages & Disadvantages", "ADVANTAGES_DISADVANTAGES", Skill.WRITING, false);
            created += upsertType("Causes, Problems & Solutions", "CAUSES_PROBLEMS_SOLUTIONS", Skill.WRITING, false);
            created += upsertType("Two-Part Question", "TWO_PART_QUESTION", Skill.WRITING, false);
            created += upsertType("Positive or Negative Development", "POSITIVE_NEGATIVE", Skill.WRITING, false);

            // --- SPEAKING ---
            created += upsertType("Speaking Part 1", "SPEAKING_PART_1", Skill.SPEAKING, false);
            created += upsertType("Speaking Part 2", "SPEAKING_PART_2", Skill.SPEAKING, false);
            created += upsertType("Speaking Part 3", "SPEAKING_PART_3", Skill.SPEAKING, false);

            log.info("Question Types upsert complete. {} new types created.", created);
        };
    }

    private int upsertType(String name, String code, Skill skill, boolean hasSharedOptions) {
        if (questionTypeRepository.findByCode(code).isPresent()) {
            return 0;
        }
        questionTypeRepository.save(createType(name, code, skill, hasSharedOptions));
        return 1;
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
