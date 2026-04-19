package io.gsp26se16.moni;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import io.gsp26se16.moni.content.entity.QuestionType;
import io.gsp26se16.moni.content.repository.QuestionTypeRepository;
import io.gsp26se16.moni.tag.entity.Tag;
import io.gsp26se16.moni.tag.repository.TagRepository;

@SpringBootTest
@ActiveProfiles("dev")
class CheckTagsTests {

    @Autowired
    TagRepository tagRepository;

    @Autowired
    QuestionTypeRepository questionTypeRepository;

    @Test
    void checkTags() {
        System.out.println("====== QUESTION TYPES ======");
        List<QuestionType> qts = questionTypeRepository.findAll();
        for (QuestionType qt : qts) {
            System.out.println(qt.getId() + " | " + qt.getCode() + " | " + qt.getName());
        }
        System.out.println("====== TAGS ======");
        List<Tag> tags = tagRepository.findAll();
        for (Tag t : tags) {
            if (t.getType() != null && t.getType().name().equals("QUESTION_TYPE")) {
                System.out.println(t.getId() + " | " + t.getCode() + " | " + t.getName());
            }
        }
        System.out.println("====================");
    }
}
