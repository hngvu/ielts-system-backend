package io.gsp26se16.moni.content.service;

import io.gsp26se16.moni.authentication.entity.UserCredentials;
import io.gsp26se16.moni.authentication.repository.UserCredentialsRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.authentication.repository.UsersRepository;
import io.gsp26se16.moni.common.enumeration.PublishStatus;
import io.gsp26se16.moni.common.enumeration.Skill;
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
    private final UserCredentialsRepository userCredentialsRepository;

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
        stimulus.setStatus(PublishStatus.DRAFT);

        Users adminUser = getCurrentUser();
        stimulus.setCreatedBy(adminUser);

        Stimulus savedStimulus = stimulusRepository.save(stimulus);

        if (request.getQuestionGroups() != null) {
            for (var groupReq : request.getQuestionGroups()) {
                QuestionGroup group = new QuestionGroup();
                group.setStimulus(savedStimulus);
                group.setInstruction(groupReq.getInstruction());
                QuestionGroup savedGroup = questionGroupRepository.save(group);

                for (var qReq : groupReq.getQuestions()) {
                    Question question = new Question();
                    question.setQuestionGroup(savedGroup);
                    question.setContent(qReq.getContent());
                    question.setPosition(qReq.getPosition());
                    question.setExplanation(qReq.getExplanation());

                    // Lưu Tag cho Câu hỏi
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
        }
        return savedStimulus.getId();
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

    // --- Helper lấy User từ JWT Token ---
    private Users getCurrentUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("Chưa xác thực (Unauthenticated)");
        }

        String credentialId = null;
        if (authentication.getPrincipal() instanceof org.springframework.security.oauth2.jwt.Jwt jwt) {
            credentialId = jwt.getClaimAsString("userId"); // Tùy thuộc vào claim bạn config trong token
        }

        if (credentialId == null) {
            throw new RuntimeException("Token không hợp lệ (Không tìm thấy userId)");
        }

        UserCredentials credentials = userCredentialsRepository.findById(credentialId)
                .orElseThrow(() -> new RuntimeException("Tài khoản không tồn tại"));

        if (credentials.getUser() == null) {
            throw new RuntimeException("Lỗi dữ liệu: UserCredentials không gắn với Users nào");
        }
        return credentials.getUser();
    }
}
