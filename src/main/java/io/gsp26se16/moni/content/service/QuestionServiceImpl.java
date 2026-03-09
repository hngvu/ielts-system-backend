package io.gsp26se16.moni.content.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.gsp26se16.moni.content.dto.request.QuestionUpdateRequest;
import io.gsp26se16.moni.content.entity.Question;
import io.gsp26se16.moni.content.repository.QuestionRepository;
import io.gsp26se16.moni.tag.entity.Tag;
import io.gsp26se16.moni.tag.repository.TagRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class QuestionServiceImpl implements QuestionService {

    private final QuestionRepository questionRepository;
    private final TagRepository tagRepository;

    @Override
    @Transactional
    public void updateQuestion(Integer id, QuestionUpdateRequest request) {
        Question question =
                questionRepository.findById(id).orElseThrow(() -> new RuntimeException("Question not found"));

        if (request.getContent() != null) question.setContent(request.getContent());
        if (request.getExplanation() != null) question.setExplanation(request.getExplanation());
        if (request.getPosition() != null) {
            question.setPosition(request.getPosition());
        }

        // Cập nhật Tag (Liên kết với bảng question_tag)
        if (request.getTagIds() != null) {
            List<Tag> newTags = tagRepository.findAllById(request.getTagIds());
            question.getTags().clear();
            question.getTags().addAll(newTags);
        }

        questionRepository.save(question);
    }
}
