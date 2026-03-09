package io.gsp26se16.moni.content.service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
        test.setStatus(PublishStatus.DRAFT);

        // Add tag cho Test
        if (request.getTagIds() != null) {
            test.getTags().addAll(tagRepository.findAllById(request.getTagIds()));
        }
        Test savedTest = testRepository.save(test);
        Users adminUser = userRepository.getReferenceById("1");

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
                    QuestionGroup savedGroup = questionGroupRepository.save(group);

                    for (var qReq : groupReq.getQuestions()) {
                        Question question = new Question();
                        question.setQuestionGroup(savedGroup);
                        question.setContent(qReq.getContent());
                        question.setPosition(qReq.getPosition());
                        question.setExplanation(qReq.getExplanation());

                        // Add tag cho Question
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

        return testPage.map(test -> TestResponse.builder()
                .id(test.getId())
                .title(test.getTitle())
                .skill(test.getSkill())
                .testType(test.getTestType())
                .duration(test.getDuration())
                .testMode(test.getTestMode())
                .status(test.getStatus())
                // Lấy ra danh sách các tag ID của Test này
                .tagIds(test.getTags().stream().map(Tag::getId).collect(Collectors.toList()))
                .build());
    }

    @Override
    public TestDetailResponse getTestDetail(Integer id) {
        Test test = testRepository.findById(id).orElseThrow(() -> new RuntimeException("Test not found"));

        List<TestStructure> structures = testStructureRepository.findByTestId(test.getId());
        structures.sort(Comparator.comparingInt(TestStructure::getSection));

        List<TestDetailResponse.StimulusDetail> stimulusDetails = structures.stream()
                .map(ts -> {
                    Stimulus s = ts.getStimulus();

                    // Map QuestionGroup
                    List<TestDetailResponse.QuestionGroupDetail> groupDetails = s.getQuestionGroups().stream()
                            .map(g -> {

                                // Map Question
                                List<TestDetailResponse.QuestionDetail> qDetails = g.getQuestions().stream()
                                        .map(q -> {

                                            // Map Option
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
                                                    // Lấy danh sách tag ID của Câu hỏi này
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
                            .section(ts.getSection()) // Lấy section từ TestStructure
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
                // Lấy danh sách tag ID của Test
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
}
