package io.gsp26se16.moni.content.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.gsp26se16.moni.common.enumeration.PublishStatus;
import io.gsp26se16.moni.common.enumeration.Skill;
import io.gsp26se16.moni.common.enumeration.TestMode;
import io.gsp26se16.moni.common.exception.AppException;
import io.gsp26se16.moni.common.exception.ErrorCode;
import io.gsp26se16.moni.content.dto.request.AutoFullTestRequest;
import io.gsp26se16.moni.content.dto.request.CreateFullTestRequest;
import io.gsp26se16.moni.content.dto.response.FullTestResponse;
import io.gsp26se16.moni.content.dto.response.StimulusOption;
import io.gsp26se16.moni.content.entity.Stimulus;
import io.gsp26se16.moni.content.entity.Test;
import io.gsp26se16.moni.content.entity.TestStructure;
import io.gsp26se16.moni.content.repository.StimulusRepository;
import io.gsp26se16.moni.content.repository.TestRepository;
import io.gsp26se16.moni.content.repository.TestStructureRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FullTestServiceImpl implements FullTestService {

    private static final Map<Skill, Integer> SECTION_COUNT = Map.of(
            Skill.READING, 3,
            Skill.LISTENING, 4,
            Skill.SPEAKING, 3);

    private static final Map<Skill, Integer> DEFAULT_DURATION = Map.of(
            Skill.READING, 3600,
            Skill.LISTENING, 1800,
            Skill.SPEAKING, 900);

    private final TestRepository testRepository;
    private final StimulusRepository stimulusRepository;
    private final TestStructureRepository testStructureRepository;

    @Override
    public List<FullTestResponse> listFullTests() {
        List<Test> tests = testRepository.findByTestModeOrderByIdDesc(TestMode.FULL_TEST);
        return tests.stream().map(this::toFullTestResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public FullTestResponse createFullTest(CreateFullTestRequest request) {
        Skill skill = parseSkill(request.getSkill());
        int requiredSections = SECTION_COUNT.getOrDefault(skill, 3);

        if (request.getStimulusIds() == null || request.getStimulusIds().size() != requiredSections) {
            throw new AppException(ErrorCode.INVALID_KEY);
        }

        Test test = buildTest(request.getTitle(), skill, request.getDuration(), PublishStatus.PUBLISHED);
        Test saved = testRepository.save(test);

        List<Integer> stimulusIds = request.getStimulusIds();
        for (int i = 0; i < stimulusIds.size(); i++) {
            Stimulus stimulus = stimulusRepository
                    .findById(stimulusIds.get(i))
                    .orElseThrow(() -> new AppException(ErrorCode.STIMULUS_NOT_FOUND));
            TestStructure ts = new TestStructure();
            ts.setTest(saved);
            ts.setStimulus(stimulus);
            ts.setSection(i + 1);
            testStructureRepository.save(ts);
        }

        return toFullTestResponse(saved);
    }

    @Override
    @Transactional
    public FullTestResponse autoGenerateFullTest(AutoFullTestRequest request) {
        Skill skill = parseSkill(request.getSkill());
        int requiredSections = SECTION_COUNT.getOrDefault(skill, 3);

        // Get all PRACTICE tests for this skill, group by section via TestStructure
        List<Test> practiceTests = testRepository.findByTestModeAndSkill(TestMode.PRACTICE, skill);

        // Build section -> stimuli map from TestStructure
        Map<Integer, List<Stimulus>> stimuliBySection = new LinkedHashMap<>();
        for (Test practiceTest : practiceTests) {
            List<TestStructure> structures = testStructureRepository.findByTestId(practiceTest.getId());
            for (TestStructure ts : structures) {
                int section = ts.getSection() != null ? ts.getSection() : 1;
                stimuliBySection
                        .computeIfAbsent(section, k -> new ArrayList<>())
                        .add(ts.getStimulus());
            }
        }

        // Validate enough stimuli per section
        for (int section = 1; section <= requiredSections; section++) {
            List<Stimulus> available = stimuliBySection.getOrDefault(section, List.of());
            if (available.isEmpty()) {
                String sectionLabel = "Section " + section;
                String skillLabel =
                        skill.name().charAt(0) + skill.name().substring(1).toLowerCase();
                throw new AppException(ErrorCode.INVALID_KEY) {
                    @Override
                    public String getMessage() {
                        return "Thiếu bài " + sectionLabel + " cho kỹ năng " + skillLabel
                                + ". Vui lòng tạo thêm bài lẻ.";
                    }
                };
            }
        }

        // Auto-generate title if not provided
        String title = request.getTitle();
        if (title == null || title.isBlank()) {
            long count = testRepository.countByTestModeAndSkill(TestMode.FULL_TEST, skill);
            String skillDisplay =
                    skill.name().charAt(0) + skill.name().substring(1).toLowerCase();
            title = skillDisplay + " Full Test #" + (count + 1);
        }

        Test test = buildTest(title, skill, null, PublishStatus.PUBLISHED);
        Test saved = testRepository.save(test);

        // Random pick 1 stimulus per section
        for (int section = 1; section <= requiredSections; section++) {
            List<Stimulus> available = new ArrayList<>(stimuliBySection.get(section));
            Collections.shuffle(available);
            Stimulus chosen = available.get(0);

            TestStructure ts = new TestStructure();
            ts.setTest(saved);
            ts.setStimulus(chosen);
            ts.setSection(section);
            testStructureRepository.save(ts);
        }

        return toFullTestResponse(saved);
    }

    @Override
    @Transactional
    public void deleteFullTest(Integer id) {
        Test test = testRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.TEST_NOT_FOUND));
        if (test.getTestMode() != TestMode.FULL_TEST) {
            throw new AppException(ErrorCode.INVALID_KEY);
        }
        testStructureRepository.deleteByTestId(id);
        test.getTags().clear();
        testRepository.delete(test);
    }

    @Override
    public Map<Integer, List<StimulusOption>> getAvailableStimuli(String skill) {
        Skill skillEnum = parseSkill(skill);
        List<Test> practiceTests = testRepository.findByTestModeAndSkill(TestMode.PRACTICE, skillEnum);

        Map<Integer, List<StimulusOption>> result = new LinkedHashMap<>();

        for (Test practiceTest : practiceTests) {
            List<TestStructure> structures = testStructureRepository.findByTestId(practiceTest.getId());
            for (TestStructure ts : structures) {
                int section = ts.getSection() != null ? ts.getSection() : 1;
                Stimulus stimulus = ts.getStimulus();

                int questionCount = stimulus.getQuestionGroups().stream()
                        .mapToInt(qg -> qg.getQuestions().size())
                        .sum();

                // Use stimulus title, fallback to practice test title
                String title =
                        stimulus.getTitle() != null && !stimulus.getTitle().isBlank()
                                ? stimulus.getTitle()
                                : practiceTest.getTitle();

                StimulusOption option = StimulusOption.builder()
                        .stimulusId(stimulus.getId())
                        .title(title)
                        .questionCount(questionCount)
                        .build();

                result.computeIfAbsent(section, k -> new ArrayList<>()).add(option);
            }
        }

        return result;
    }

    // --- helpers ---

    private Test buildTest(String title, Skill skill, Integer duration, PublishStatus status) {
        Test test = new Test();
        test.setTitle(title);
        test.setSkill(skill);
        test.setTestMode(TestMode.FULL_TEST);
        test.setStatus(status);
        test.setDuration(duration != null ? duration : DEFAULT_DURATION.getOrDefault(skill, 3600));
        return test;
    }

    private FullTestResponse toFullTestResponse(Test test) {
        List<TestStructure> structures = testStructureRepository.findByTestId(test.getId());
        structures.sort((a, b) -> {
            int sa = a.getSection() != null ? a.getSection() : 0;
            int sb = b.getSection() != null ? b.getSection() : 0;
            return Integer.compare(sa, sb);
        });

        List<FullTestResponse.StimulusInfo> stimuliInfo = structures.stream()
                .map(ts -> {
                    Stimulus s = ts.getStimulus();
                    String title =
                            s.getTitle() != null && !s.getTitle().isBlank() ? s.getTitle() : "(Không có tiêu đề)";
                    return FullTestResponse.StimulusInfo.builder()
                            .id(s.getId())
                            .section(ts.getSection())
                            .title(title)
                            .build();
                })
                .collect(Collectors.toList());

        return FullTestResponse.builder()
                .id(test.getId())
                .title(test.getTitle())
                .skill(test.getSkill() != null ? test.getSkill().name() : null)
                .duration(test.getDuration())
                .status(test.getStatus() != null ? test.getStatus().name() : null)
                .stimuli(stimuliInfo)
                .build();
    }

    private Skill parseSkill(String skill) {
        try {
            return Skill.valueOf(skill.toUpperCase());
        } catch (Exception e) {
            throw new AppException(ErrorCode.INVALID_KEY);
        }
    }
}
