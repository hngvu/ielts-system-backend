package io.gsp26se16.moni.content.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.authentication.repository.UsersRepository;
import io.gsp26se16.moni.common.enumeration.PublishStatus;
import io.gsp26se16.moni.common.enumeration.Skill;
import io.gsp26se16.moni.common.exception.AppException;
import io.gsp26se16.moni.common.exception.ErrorCode;
import io.gsp26se16.moni.content.dto.request.StimulusCreateRequest;
import io.gsp26se16.moni.content.dto.response.StimulusResponse;
import io.gsp26se16.moni.content.entity.Question;
import io.gsp26se16.moni.content.entity.QuestionGroup;
import io.gsp26se16.moni.content.entity.QuestionOption;
import io.gsp26se16.moni.content.entity.Stimulus;
import io.gsp26se16.moni.content.repository.QuestionGroupRepository;
import io.gsp26se16.moni.content.repository.QuestionRepository;
import io.gsp26se16.moni.content.repository.StimulusRepository;
import io.gsp26se16.moni.tag.repository.TagRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StimulusServiceImpl implements StimulusService {

    private final StimulusRepository stimulusRepository;
    private final QuestionGroupRepository questionGroupRepository;
    private final QuestionRepository questionRepository;
    private final TagRepository tagRepository;
    private final UsersRepository userRepository;
    private final TranscriptService transcriptService;
    private final io.gsp26se16.moni.ai.writing.service.GeminiVisionClient geminiVisionClient;

    @Override
    @Transactional
    public Integer createStimulus(StimulusCreateRequest request) {
        Stimulus stimulus = new Stimulus();
        stimulus.setTitle(request.getTitle());
        stimulus.setTestType(request.getTestType());
        stimulus.setSkill(request.getSkill());
        stimulus.setContent(request.getContent());
        stimulus.setMediaUrl(request.getMediaUrl());
        stimulus.setMetadata(request.getMetadata());
        stimulus.setStatus(PublishStatus.PUBLISHED);

        Users adminUser = userRepository.getReferenceById("1");
        stimulus.setCreatedBy(adminUser);

        Stimulus savedStimulus = stimulusRepository.save(stimulus);

        if (request.getQuestionGroups() != null) {
            for (var groupReq : request.getQuestionGroups()) {
                QuestionGroup group = new QuestionGroup();
                group.setStimulus(savedStimulus);
                group.setInstruction(groupReq.getInstruction());
                QuestionGroup savedGroup = questionGroupRepository.save(group);

                // ── Pass 1: Lưu tất cả questions, track theo position ──
                Map<Integer, Question> positionToQuestion = new HashMap<>();

                for (var qReq : groupReq.getQuestions()) {
                    Question question = new Question();
                    question.setQuestionGroup(savedGroup);
                    question.setContent(qReq.getContent());
                    question.setPosition(qReq.getPosition());
                    question.setQuestionCategory(qReq.getQuestionCategory());
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
                    Question saved = questionRepository.save(question);
                    positionToQuestion.put(saved.getPosition(), saved);
                }

                // ── Pass 2: Link follow-up → parent theo parentQuestionPosition ──
                for (var qReq : groupReq.getQuestions()) {
                    if (qReq.getParentQuestionPosition() != null) {
                        Question child = positionToQuestion.get(qReq.getPosition());
                        Question parent = positionToQuestion.get(qReq.getParentQuestionPosition());
                        if (child != null && parent != null) {
                            child.setParentQuestion(parent);
                            questionRepository.save(child);
                        }
                    }
                }
            }
        }
        return savedStimulus.getId();
    }

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public StimulusResponse updateStimulus(Integer id, String content, String mediaUrl, Object transcript) {
        Stimulus stimulus =
                stimulusRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.STIMULUS_NOT_FOUND));
        if (content != null) stimulus.setContent(content);
        if (mediaUrl != null) stimulus.setMediaUrl(mediaUrl);
        if (transcript != null) stimulus.setTranscript((List<Map<String, Object>>) transcript);
        Stimulus saved = stimulusRepository.save(stimulus);
        return StimulusResponse.builder()
                .id(saved.getId())
                .title(saved.getTitle())
                .skill(saved.getSkill())
                .status(saved.getStatus())
                .build();
    }

    @Override
    public Page<StimulusResponse> getAllStimuli(String keyword, Skill skill, Pageable pageable) {
        Page<Stimulus> stimuliPage = stimulusRepository.searchStimuli(keyword, skill, pageable);

        return stimuliPage.map(s -> StimulusResponse.builder()
                .id(s.getId())
                .title(s.getTitle())
                .skill(s.getSkill())
                .status(s.getStatus())
                .build());
    }

    @Override
    @Transactional
    public List<Map<String, Object>> transcribeAndSave(Integer stimulusId) {
        Stimulus stimulus = stimulusRepository
                .findById(stimulusId)
                .orElseThrow(() -> new AppException(ErrorCode.STIMULUS_NOT_FOUND));
        if (stimulus.getMediaUrl() == null || stimulus.getMediaUrl().isBlank()) {
            throw new RuntimeException("Stimulus không có audio URL");
        }
        List<Map<String, Object>> transcript = transcriptService.transcribeAudio(stimulus.getMediaUrl());
        stimulus.setTranscript(transcript);
        stimulusRepository.save(stimulus);
        return transcript;
    }

    @Override
    public List<Map<String, Object>> transcribeByUrl(String audioUrl) {
        if (audioUrl == null || audioUrl.isBlank()) {
            throw new RuntimeException("audioUrl không được để trống");
        }
        return transcriptService.transcribeAudio(audioUrl);
    }

    @Override
    public List<Map<String, Object>> getTranscript(Integer stimulusId) {
        Stimulus stimulus = stimulusRepository
                .findById(stimulusId)
                .orElseThrow(() -> new AppException(ErrorCode.STIMULUS_NOT_FOUND));
        return stimulus.getTranscript();
    }

    @Override
    @Transactional
    public Map<String, Object> analyzeChart(Integer stimulusId, byte[] imageBytes) {
        Stimulus stimulus = stimulusRepository
                .findById(stimulusId)
                .orElseThrow(() -> new AppException(ErrorCode.STIMULUS_NOT_FOUND));

        String base64Image = java.util.Base64.getEncoder().encodeToString(imageBytes);
        Map<String, Object> analysis = geminiVisionClient.analyzeChart(base64Image);

        stimulus.setVisonAnalysisResult(analysis);
        stimulusRepository.save(stimulus);
        return analysis;
    }

    @Override
    @Transactional
    public void updateVisonAnalysisResult(Integer stimulusId, Map<String, Object> visonAnalysisResult) {
        Stimulus stimulus = stimulusRepository
                .findById(stimulusId)
                .orElseThrow(() -> new AppException(ErrorCode.STIMULUS_NOT_FOUND));
        stimulus.setVisonAnalysisResult(visonAnalysisResult);
        stimulusRepository.save(stimulus);
    }

    @Override
    public Map<String, Object> getVisonAnalysisResult(Integer stimulusId) {
        Stimulus stimulus = stimulusRepository
                .findById(stimulusId)
                .orElseThrow(() -> new AppException(ErrorCode.STIMULUS_NOT_FOUND));
        return stimulus.getVisonAnalysisResult();
    }
}
