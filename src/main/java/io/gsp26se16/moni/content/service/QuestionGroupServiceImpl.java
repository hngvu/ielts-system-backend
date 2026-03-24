package io.gsp26se16.moni.content.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.gsp26se16.moni.common.exception.AppException;
import io.gsp26se16.moni.common.exception.ErrorCode;
import io.gsp26se16.moni.content.dto.request.QuestionCreateRequest;
import io.gsp26se16.moni.content.dto.request.QuestionGroupCreateRequest;
import io.gsp26se16.moni.content.entity.Question;
import io.gsp26se16.moni.content.entity.QuestionGroup;
import io.gsp26se16.moni.content.entity.QuestionOption;
import io.gsp26se16.moni.content.entity.Stimulus;
import io.gsp26se16.moni.content.repository.QuestionGroupRepository;
import io.gsp26se16.moni.content.repository.QuestionRepository;
import io.gsp26se16.moni.content.repository.QuestionTypeRepository;
import io.gsp26se16.moni.content.repository.StimulusRepository;
import io.gsp26se16.moni.tag.repository.TagRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class QuestionGroupServiceImpl implements QuestionGroupService {

    private final StimulusRepository stimulusRepository;
    private final QuestionGroupRepository questionGroupRepository;
    private final QuestionTypeRepository questionTypeRepository;
    private final QuestionRepository questionRepository;
    private final TagRepository tagRepository;

    @Override
    @Transactional
    public Integer createForStimulus(Integer stimulusId, QuestionGroupCreateRequest request) {
        Stimulus stimulus = stimulusRepository
                .findById(stimulusId)
                .orElseThrow(() -> new AppException(ErrorCode.STIMULUS_NOT_FOUND));

        QuestionGroup group = new QuestionGroup();
        group.setStimulus(stimulus);
        group.setInstruction(request.getInstruction());
        group.setGroupContent(request.getGroupContent());
        group.setImageUrl(request.getImageUrl());
        group.setSharedOptions(request.getSharedOptions());

        if (request.getQuestionTypeCode() != null) {
            questionTypeRepository.findByCode(request.getQuestionTypeCode()).ifPresent(group::setQuestionType);
        }

        QuestionGroup savedGroup = questionGroupRepository.save(group);

        if (request.getQuestions() != null) {
            for (QuestionCreateRequest qReq : request.getQuestions()) {
                saveQuestion(savedGroup, qReq);
            }
        }

        return savedGroup.getId();
    }

    @Override
    @Transactional
    public void deleteQuestionGroup(Integer id) {
        QuestionGroup group = questionGroupRepository
                .findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.QUESTION_GROUP_NOT_FOUND));
        questionGroupRepository.delete(group);
    }

    @Override
    @Transactional
    public void updateImageUrl(Integer id, String imageUrl) {
        QuestionGroup group = questionGroupRepository
                .findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.QUESTION_GROUP_NOT_FOUND));
        group.setImageUrl(imageUrl);
        questionGroupRepository.save(group);
    }

    @Override
    @Transactional
    public void updateGroupContent(Integer id, String groupContent) {
        QuestionGroup group = questionGroupRepository
                .findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.QUESTION_GROUP_NOT_FOUND));
        group.setGroupContent(groupContent);
        questionGroupRepository.save(group);
    }

    @Override
    @Transactional
    public void updateQuestionTypeCode(Integer id, String questionTypeCode) {
        QuestionGroup group = questionGroupRepository
                .findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.QUESTION_GROUP_NOT_FOUND));
        if (questionTypeCode != null) {
            questionTypeRepository.findByCode(questionTypeCode).ifPresent(group::setQuestionType);
        }
        questionGroupRepository.save(group);
    }

    // shared helper — also used by QuestionServiceImpl via QuestionGroupServiceImpl injection
    void saveQuestion(QuestionGroup group, QuestionCreateRequest qReq) {
        Question question = new Question();
        question.setQuestionGroup(group);
        question.setContent(qReq.getContent());
        if (qReq.getPosition() != null) question.setPosition(qReq.getPosition());
        question.setExplanation(qReq.getExplanation());

        if (qReq.getTagIds() != null) {
            question.getTags().addAll(tagRepository.findAllById(qReq.getTagIds()));
        }

        if (qReq.getOptions() != null) {
            for (var optReq : qReq.getOptions()) {
                QuestionOption option = new QuestionOption();
                option.setLabel(optReq.getLabel());
                option.setContent(optReq.getContent());
                option.setCorrect(optReq.getIsCorrect());
                option.setQuestion(question);
                question.getOptions().add(option);
            }
        }

        questionRepository.save(question);
    }
}
