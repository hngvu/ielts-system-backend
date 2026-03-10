package io.gsp26se16.moni.content.service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.authentication.repository.UsersRepository;
import io.gsp26se16.moni.common.enumeration.PublishStatus;
import io.gsp26se16.moni.common.enumeration.Skill;
import io.gsp26se16.moni.content.dto.request.TestImportRequest;
import io.gsp26se16.moni.content.dto.request.TestStructureRequest;
import io.gsp26se16.moni.content.dto.request.TestUpdateRequest;
import io.gsp26se16.moni.content.dto.response.TestDetailResponse;
import io.gsp26se16.moni.content.dto.response.TestResponse;
import io.gsp26se16.moni.content.entity.*;
import io.gsp26se16.moni.content.repository.*;
import io.gsp26se16.moni.tag.entity.Tag;
import io.gsp26se16.moni.tag.repository.TagRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TestServiceImpl implements TestService {

    private final TestRepository testRepository;
    private final StimulusRepository stimulusRepository;
    private final TestStructureRepository testStructureRepository;
    private final QuestionGroupRepository questionGroupRepository;
    private final QuestionRepository questionRepository;
    private final QuestionTypeRepository questionTypeRepository;
    private final TagRepository tagRepository;
    private final UsersRepository userRepository;

    @Override
    @Transactional
    public Integer importTest(TestImportRequest request) {
        Test test = new Test();
        test.setTitle(request.getTitle());
        test.setDescription(request.getDescription());
        test.setSkill(request.getSkill());
        test.setTestType(request.getTestType());
        test.setDuration(request.getDuration());
        test.setTestMode(request.getTestMode());
        test.setThumbnailUrl(request.getThumbnailUrl());
        test.setStatus(PublishStatus.DRAFT);

        if (request.getTagIds() != null) {
            test.getTags().addAll(tagRepository.findAllById(request.getTagIds()));
        }
        Test savedTest = testRepository.save(test);
        Users adminUser = resolveCurrentUser();

        for (var stimReq : request.getStimuli()) {
            Stimulus stimulus = new Stimulus();

            stimulus.setTitle(stimReq.getTitle());
            stimulus.setContent(stimReq.getContent());
            stimulus.setMediaUrl(stimReq.getMediaUrl());

            stimulus.setSkill(request.getSkill());
            stimulus.setTestType(request.getTestType());

            stimulus.setStatus(PublishStatus.DRAFT);
            stimulus.setCreatedBy(adminUser);
            Stimulus savedStimulus = stimulusRepository.save(stimulus);

            TestStructure testStructure = new TestStructure();
            testStructure.setTest(savedTest);
            testStructure.setStimulus(savedStimulus);
            testStructure.setSection(stimReq.getSection());
            testStructureRepository.save(testStructure);

            if (stimReq.getQuestionGroups() != null) {
                for (var groupReq : stimReq.getQuestionGroups()) {
                    QuestionGroup group = new QuestionGroup();
                    group.setStimulus(savedStimulus);
                    group.setInstruction(groupReq.getInstruction());
                    if (groupReq.getQuestionTypeCode() != null) {
                        questionTypeRepository
                                .findByCode(groupReq.getQuestionTypeCode())
                                .ifPresent(group::setQuestionType);
                    }
                    QuestionGroup savedGroup = questionGroupRepository.save(group);

                    for (var qReq : groupReq.getQuestions()) {
                        Question question = new Question();
                        question.setQuestionGroup(savedGroup);
                        question.setContent(qReq.getContent());
                        question.setPosition(qReq.getPosition());
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
            }
        }
        return savedTest.getId();
    }

    @Override
    public Page<TestResponse> getAllTests(String keyword, Skill skill, Pageable pageable) {
        Page<Test> testPage = testRepository.searchTests(keyword, skill, pageable);

        return testPage.map(test -> buildTestResponse(test));
    }

    @Override
    public Page<TestResponse> getPublishedTests(String keyword, Skill skill, Pageable pageable) {
        Page<Test> testPage = testRepository.searchByStatus(PublishStatus.PUBLISHED, keyword, skill, pageable);

        return testPage.map(test -> buildTestResponse(test));
    }

    @Override
    public TestDetailResponse getTestDetail(Integer id) {
        Test test = testRepository.findById(id).orElseThrow(() -> new RuntimeException("Test not found"));

        List<TestStructure> structures = testStructureRepository.findByTestId(test.getId());
        structures.sort(Comparator.comparingInt(TestStructure::getSection));

        List<TestDetailResponse.StimulusDetail> stimulusDetails = structures.stream()
                .map(ts -> {
                    Stimulus s = ts.getStimulus();

                    List<TestDetailResponse.QuestionGroupDetail> groupDetails = s.getQuestionGroups().stream()
                            .map(g -> {
                                List<TestDetailResponse.QuestionDetail> qDetails = g.getQuestions().stream()
                                        .map(q -> {
                                            List<TestDetailResponse.OptionDetail> optDetails = q.getOptions().stream()
                                                    .map(opt -> TestDetailResponse.OptionDetail.builder()
                                                            .id(opt.getId())
                                                            .label(opt.getLabel())
                                                            .content(opt.getContent())
                                                            .isCorrect(opt.isCorrect())
                                                            .build())
                                                    .toList();

                                            return TestDetailResponse.QuestionDetail.builder()
                                                    .id(q.getId())
                                                    .content(q.getContent())
                                                    .position(q.getPosition())
                                                    .explanation(q.getExplanation())
                                                    .tagIds(q.getTags().stream()
                                                            .map(Tag::getId)
                                                            .collect(Collectors.toList()))
                                                    .options(optDetails)
                                                    .build();
                                        })
                                        .toList();

                                return TestDetailResponse.QuestionGroupDetail.builder()
                                        .id(g.getId())
                                        .instruction(g.getInstruction())
                                        .questions(qDetails)
                                        .build();
                            })
                            .toList();

                    return TestDetailResponse.StimulusDetail.builder()
                            .id(s.getId())
                            .title(s.getTitle())
                            .content(s.getContent())
                            .mediaUrl(s.getMediaUrl())
                            .section(ts.getSection())
                            .questionGroups(groupDetails)
                            .build();
                })
                .toList();

        return TestDetailResponse.builder()
                .id(test.getId())
                .title(test.getTitle())
                .description(test.getDescription())
                .skill(test.getSkill())
                .duration(test.getDuration())
                .testMode(test.getTestMode())
                .status(test.getStatus())
                .tagIds(test.getTags().stream().map(Tag::getId).collect(Collectors.toList()))
                .stimuli(stimulusDetails)
                .build();
    }

    @Override
    @Transactional
    public void updateTest(Integer id, TestUpdateRequest request) {
        Test test = testRepository.findById(id).orElseThrow(() -> new RuntimeException("Test not found"));

        if (request.getTitle() != null) test.setTitle(request.getTitle());
        if (request.getDescription() != null) test.setDescription(request.getDescription());
        if (request.getThumbnailUrl() != null) test.setThumbnailUrl(request.getThumbnailUrl());
        if (request.getDuration() != null) test.setDuration(request.getDuration());
        if (request.getTestMode() != null) test.setTestMode(request.getTestMode());
        if (request.getStatus() != null) test.setStatus(request.getStatus());

        if (request.getTagIds() != null) {
            List<Tag> newTags = tagRepository.findAllById(request.getTagIds());
            test.getTags().clear();
            test.getTags().addAll(newTags);
        }
        testRepository.save(test);
    }

    @Override
    @Transactional
    public void deleteTest(Integer id) {
        Test test = testRepository.findById(id).orElseThrow(() -> new RuntimeException("Test not found"));
        test.setStatus(PublishStatus.HIDDEN);
        testRepository.save(test);
    }

    @Override
    @Transactional
    public void addStimulusToTest(Integer testId, TestStructureRequest request) {
        Test test = testRepository.findById(testId).orElseThrow(() -> new RuntimeException("Test not found"));
        Stimulus stimulus = stimulusRepository.getReferenceById(request.getStimulusId());

        TestStructure structure = new TestStructure();
        structure.setTest(test);
        structure.setStimulus(stimulus);
        structure.setSection(request.getSection());

        testStructureRepository.save(structure);
    }

    @Override
    @Transactional
    public void removeStimulusFromTest(Integer testId, Integer stimulusId) {
        if (!testStructureRepository.existsByTestIdAndStimulusId(testId, stimulusId)) {
            throw new RuntimeException("Ngữ liệu không nằm trong đề thi này");
        }
        testStructureRepository.deleteByTestIdAndStimulusId(testId, stimulusId);
    }

    private TestResponse buildTestResponse(Test test) {
        Integer testId = test.getId();
        return TestResponse.builder()
                .id(testId)
                .title(test.getTitle())
                .description(test.getDescription())
                .thumbnailUrl(test.getThumbnailUrl())
                .skill(test.getSkill())
                .testType(test.getTestType())
                .duration(test.getDuration())
                .testMode(test.getTestMode())
                .status(test.getStatus())
                .tagIds(test.getTags().stream().map(Tag::getId).collect(Collectors.toList()))
                .questionCount(testRepository.countQuestionsByTestId(testId))
                .attemptCount(testRepository.countAttemptsByTestId(testId))
                .questionTypes(testRepository.findQuestionTypesByTestId(testId))
                .build();
    }

    private Users resolveCurrentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            String userId = jwt.getClaim("userId");
            if (userId != null) {
                return userRepository.findById(userId).orElse(null);
            }
        }
        return null;
    }
}
