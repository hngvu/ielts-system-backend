package io.gsp26se16.moni.content.service;

import java.util.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.gsp26se16.moni.common.exception.AppException;
import io.gsp26se16.moni.common.exception.ErrorCode;
import io.gsp26se16.moni.content.dto.request.QuestionCreateRequest;
import io.gsp26se16.moni.content.dto.request.QuestionGroupCreateRequest;
import io.gsp26se16.moni.content.entity.Question;
import io.gsp26se16.moni.content.entity.QuestionGroup;
import io.gsp26se16.moni.content.entity.QuestionOption;
import io.gsp26se16.moni.content.entity.QuestionType;
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
        int nextOrderIndex = stimulus.getQuestionGroups().stream()
                        .map(QuestionGroup::getOrderIndex)
                        .filter(java.util.Objects::nonNull)
                        .max(Integer::compareTo)
                        .orElse(-1)
                + 1;
        group.setOrderIndex(nextOrderIndex);
        group.setImageUrl(request.getImageUrl());
        group.setSharedOptions(request.getSharedOptions());

        applyQuestionTypeCode(group, request.getQuestionTypeCode());

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

        QuestionType oldType = group.getQuestionType();
        applyQuestionTypeCode(group, questionTypeCode);
        QuestionGroup savedGroup = questionGroupRepository.save(group);

        QuestionType newType = savedGroup.getQuestionType();
        if ((oldType == null && newType != null) || (oldType != null && !oldType.equals(newType))) {
            syncQuestionTagsForGroup(savedGroup, oldType, newType);
        }
    }

    @Override
    @Transactional
    public void updateOrderIndex(Integer id, Integer orderIndex) {
        QuestionGroup group = questionGroupRepository
                .findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.QUESTION_GROUP_NOT_FOUND));
        group.setOrderIndex(orderIndex);
        questionGroupRepository.save(group);
    }

    @Override
    @Transactional
    public void updateInstruction(Integer id, String instruction) {
        QuestionGroup group = questionGroupRepository
                .findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.QUESTION_GROUP_NOT_FOUND));
        group.setInstruction(instruction);
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

        // Auto-attach QUESTION_TYPE tag matching the group's type
        if (group.getQuestionType() != null) {
            String tagCode = "QT_" + group.getQuestionType().getCode();
            tagRepository.findByCode(tagCode).ifPresent(tag -> question.getTags()
                    .add(tag));
        }

        questionRepository.save(question);
    }

    private void applyQuestionTypeCode(QuestionGroup group, String questionTypeCode) {
        if (questionTypeCode == null) {
            group.setQuestionType(null);
            return;
        }

        String normalizedCode = questionTypeCode.trim();
        if (normalizedCode.isEmpty()) {
            group.setQuestionType(null);
            return;
        }

        group.setQuestionType(questionTypeRepository
                .findByCode(normalizedCode)
                .orElseThrow(() -> new AppException(ErrorCode.QUESTION_TYPE_NOT_FOUND)));
    }

    private void syncQuestionTagsForGroup(QuestionGroup group, QuestionType oldType, QuestionType newType) {
        if (group.getQuestions() == null || group.getQuestions().isEmpty()) {
            return;
        }
        io.gsp26se16.moni.tag.entity.Tag oldTag = oldType != null
                ? tagRepository.findByCode("QT_" + oldType.getCode()).orElse(null)
                : null;
        io.gsp26se16.moni.tag.entity.Tag newTag = newType != null
                ? tagRepository.findByCode("QT_" + newType.getCode()).orElse(null)
                : null;

        for (Question q : group.getQuestions()) {
            boolean changed = false;
            if (oldTag != null && q.getTags().contains(oldTag)) {
                q.getTags().remove(oldTag);
                changed = true;
            }
            if (newTag != null && !q.getTags().contains(newTag)) {
                q.getTags().add(newTag);
                changed = true;
            }
            if (changed) {
                questionRepository.save(q);
            }
        }
    }

    /**
     * Find all question groups with generic "MATCHING" type and reclassify them
     * to specific types: MATCHING_HEADINGS, MATCHING_INFORMATION, or MATCHING_FEATURE.
     */
    @Transactional
    public Map<String, Object> fixGenericMatchingTypes() {
        List<QuestionGroup> allGroups = questionGroupRepository.findAll();
        int updated = 0;
        int matchedHeadings = 0;
        int matchedInformation = 0;
        int matchedFeature = 0;

        Optional<QuestionType> matchingHeadingsType = questionTypeRepository.findByCode("MATCHING_HEADINGS");
        Optional<QuestionType> matchingInformationType = questionTypeRepository.findByCode("MATCHING_INFORMATION");
        Optional<QuestionType> matchingFeatureType = questionTypeRepository.findByCode("MATCHING_FEATURE");

        for (QuestionGroup group : allGroups) {
            if (group.getQuestionType() == null
                    || !"MATCHING".equals(group.getQuestionType().getCode())) {
                continue;
            }

            // Reclassify based on sharedOptions labels and instruction
            String newTypeCode = classifyMatchingGroup(group);

            Optional<QuestionType> newType =
                    switch (newTypeCode) {
                        case "MATCHING_HEADINGS" -> matchingHeadingsType;
                        case "MATCHING_INFORMATION" -> matchingInformationType;
                        case "MATCHING_FEATURE" -> matchingFeatureType;
                        default -> Optional.empty();
                    };

            if (newType.isPresent()) {
                group.setQuestionType(newType.get());
                questionGroupRepository.save(group);
                updated++;

                switch (newTypeCode) {
                    case "MATCHING_HEADINGS" -> matchedHeadings++;
                    case "MATCHING_INFORMATION" -> matchedInformation++;
                    case "MATCHING_FEATURE" -> matchedFeature++;
                }
            }
        }

        return Map.of(
                "totalScanned", allGroups.size(),
                "totalUpdated", updated,
                "matchingHeadings", matchedHeadings,
                "matchingInformation", matchedInformation,
                "matchingFeature", matchedFeature);
    }

    /**
     * Classify a matching question group into the correct specific type.
     */
    private String classifyMatchingGroup(QuestionGroup group) {
        String instruction =
                group.getInstruction() == null ? "" : group.getInstruction().toLowerCase(Locale.ROOT);

        // Check instruction keywords first
        if (instruction.contains("heading")) return "MATCHING_HEADINGS";
        if (instruction.contains("information")) return "MATCHING_INFORMATION";
        if (instruction.contains("feature")) return "MATCHING_FEATURE";

        // Analyze sharedOptions labels
        if (group.getSharedOptions() != null && !group.getSharedOptions().isEmpty()) {
            List<String> labels = group.getSharedOptions().stream()
                    .map(opt -> opt.get("label") != null
                            ? opt.get("label").toString().trim()
                            : "")
                    .toList();

            // MATCHING_HEADINGS: labels look like roman numerals (i, ii, iii, iv, v, vi, vii, viii, ix, x, etc.)
            boolean isRomanNumerals = labels.stream().allMatch(l -> l.matches("^[ivxlc]+$"));
            if (isRomanNumerals) return "MATCHING_HEADINGS";

            // MATCHING_INFORMATION: labels are paragraph/section identifiers (A, B, C, D or "Paragraph A", etc.)
            boolean isParagraphLabels = labels.stream()
                    .allMatch(l -> l.matches("^[A-Z]$")
                            || l.toLowerCase(Locale.ROOT).startsWith("paragraph")
                            || l.toLowerCase(Locale.ROOT).startsWith("section"));
            if (isParagraphLabels) return "MATCHING_INFORMATION";

            // Default: anything else with shared options is matching features
            return "MATCHING_FEATURE";
        }

        // Fallback: if no shared options, default to matching feature
        return "MATCHING_FEATURE";
    }
}
