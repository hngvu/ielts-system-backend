package io.gsp26se16.moni.content.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.gsp26se16.moni.common.exception.AppException;
import io.gsp26se16.moni.common.exception.ErrorCode;
import io.gsp26se16.moni.content.dto.request.QuestionCreateRequest;
import io.gsp26se16.moni.content.dto.request.QuestionUpdateRequest;
import io.gsp26se16.moni.content.entity.Question;
import io.gsp26se16.moni.content.entity.QuestionGroup;
import io.gsp26se16.moni.content.entity.QuestionOption;
import io.gsp26se16.moni.content.repository.QuestionGroupRepository;
import io.gsp26se16.moni.content.repository.QuestionRepository;
import io.gsp26se16.moni.tag.entity.Tag;
import io.gsp26se16.moni.tag.repository.TagRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class QuestionServiceImpl implements QuestionService {

    private final QuestionRepository questionRepository;
    private final QuestionGroupRepository questionGroupRepository;
    private final TagRepository tagRepository;

    @Override
    @Transactional
    public void updateQuestion(Integer id, QuestionUpdateRequest request) {
        Question question =
                questionRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.QUESTION_NOT_FOUND));

        if (request.getContent() != null) question.setContent(request.getContent());
        if (request.getExplanation() != null) question.setExplanation(request.getExplanation());
        if (request.getPosition() != null) question.setPosition(request.getPosition());

        if (request.getTagIds() != null) {
            List<Tag> newTags = tagRepository.findAllById(request.getTagIds());
            question.getTags().clear();
            question.getTags().addAll(newTags);
        }

        // Update options in-place to preserve IDs (avoid FK violations from attempt_answer)
        if (request.getOptions() != null) {
            var existingByLabel = new java.util.LinkedHashMap<String, QuestionOption>();
            for (var opt : question.getOptions()) {
                existingByLabel.put(opt.getLabel(), opt);
            }
            // Remove options whose labels are no longer present
            var requestLabels = new java.util.HashSet<String>();
            for (var optReq : request.getOptions()) {
                requestLabels.add(optReq.getLabel());
            }
            question.getOptions().removeIf(opt -> !requestLabels.contains(opt.getLabel()));
            // Update existing or add new
            for (var optReq : request.getOptions()) {
                QuestionOption existing = existingByLabel.get(optReq.getLabel());
                if (existing != null) {
                    existing.setContent(optReq.getContent());
                    existing.setCorrect(optReq.getIsCorrect());
                } else {
                    QuestionOption option = new QuestionOption();
                    option.setLabel(optReq.getLabel());
                    option.setContent(optReq.getContent());
                    option.setCorrect(optReq.getIsCorrect());
                    option.setQuestion(question);
                    question.getOptions().add(option);
                }
            }
        }

        questionRepository.save(question);
    }

    @Override
    @Transactional
    public void batchUpdateQuestions(Map<Integer, QuestionUpdateRequest> updates) {
        updates.forEach(this::updateQuestion);
    }

    @Override
    @Transactional
    public Integer createForGroup(Integer groupId, QuestionCreateRequest request) {
        QuestionGroup group = questionGroupRepository
                .findById(groupId)
                .orElseThrow(() -> new AppException(ErrorCode.QUESTION_GROUP_NOT_FOUND));

        Question question = new Question();
        question.setQuestionGroup(group);
        question.setContent(request.getContent());
        if (request.getPosition() != null) question.setPosition(request.getPosition());
        question.setExplanation(request.getExplanation());

        if (request.getTagIds() != null) {
            question.getTags().addAll(tagRepository.findAllById(request.getTagIds()));
        }

        if (request.getOptions() != null) {
            for (var optReq : request.getOptions()) {
                QuestionOption option = new QuestionOption();
                option.setLabel(optReq.getLabel());
                option.setContent(optReq.getContent());
                option.setCorrect(optReq.getIsCorrect());
                option.setQuestion(question);
                question.getOptions().add(option);
            }
        }

        Question saved = questionRepository.save(question);
        return saved.getId();
    }

    @Override
    @Transactional
    public void deleteQuestion(Integer id) {
        Question question =
                questionRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.QUESTION_NOT_FOUND));
        questionRepository.delete(question);
    }
}
