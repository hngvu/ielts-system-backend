package io.gsp26se16.moni.content.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.gsp26se16.moni.common.enumeration.PublishStatus;
import io.gsp26se16.moni.common.enumeration.Skill;
import io.gsp26se16.moni.common.enumeration.TestMode;
import io.gsp26se16.moni.common.enumeration.TestType;
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
            Skill.WRITING, 2,
            Skill.SPEAKING, 3);

    private static final Map<Skill, Integer> DEFAULT_DURATION = Map.of(
            Skill.READING, 60,
            Skill.LISTENING, 30,
            Skill.WRITING, 60,
            Skill.SPEAKING, 15);

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

        List<Stimulus> selectedStimuli = fetchStimuliByIds(request.getStimulusIds());
        validateReadingQuestionTotal(skill, selectedStimuli);

        PublishStatus status =
                request.getStatus() != null && !request.getStatus().isBlank()
                        ? parseStatus(request.getStatus())
                        : PublishStatus.PUBLISHED;

        Test test = buildTest(request.getTitle(), skill, request.getDuration(), status);
        if (request.getTestType() != null && !request.getTestType().isBlank()) {
            test.setTestType(parseTestType(request.getTestType()));
        }

        Test saved = testRepository.save(test);

        for (int i = 0; i < selectedStimuli.size(); i++) {
            TestStructure ts = new TestStructure();
            ts.setTest(saved);
            ts.setStimulus(selectedStimuli.get(i));
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

        List<Test> practiceTests = testRepository.findByTestModeAndSkill(TestMode.PRACTICE, skill);

        Map<Integer, List<Stimulus>> stimuliBySection = new LinkedHashMap<>();
        for (Test practiceTest : practiceTests) {
            List<TestStructure> structures = testStructureRepository.findByTestId(practiceTest.getId());
            for (TestStructure ts : structures) {
                int section = resolveSection(practiceTest, ts);
                stimuliBySection
                        .computeIfAbsent(section, k -> new ArrayList<>())
                        .add(ts.getStimulus());
            }
        }

        for (int section = 1; section <= requiredSections; section++) {
            List<Stimulus> available = stimuliBySection.getOrDefault(section, List.of());
            if (available.isEmpty()) {
                String sectionLabel = "Section " + section;
                String skillLabel =
                        skill.name().charAt(0) + skill.name().substring(1).toLowerCase();
                throw new AppException(ErrorCode.INVALID_KEY) {
                    @Override
                    public String getMessage() {
                        return "Thieu bai " + sectionLabel + " cho ky nang " + skillLabel
                                + ". Vui long tao them bai le.";
                    }
                };
            }
        }

        String title = request.getTitle();
        if (title == null || title.isBlank()) {
            long count = testRepository.countByTestModeAndSkill(TestMode.FULL_TEST, skill);
            String skillDisplay =
                    skill.name().charAt(0) + skill.name().substring(1).toLowerCase();
            title = skillDisplay + " Full Test #" + (count + 1);
        }

        Test test = buildTest(title, skill, null, PublishStatus.PUBLISHED);
        Test saved = testRepository.save(test);

        if (skill == Skill.READING) {
            List<List<Stimulus>> validCombinations = new ArrayList<>();
            List<Stimulus> sectionOne = stimuliBySection.getOrDefault(1, List.of());
            List<Stimulus> sectionTwo = stimuliBySection.getOrDefault(2, List.of());
            List<Stimulus> sectionThree = stimuliBySection.getOrDefault(3, List.of());

            for (Stimulus s1 : sectionOne) {
                for (Stimulus s2 : sectionTwo) {
                    for (Stimulus s3 : sectionThree) {
                        int total = countQuestions(s1) + countQuestions(s2) + countQuestions(s3);
                        if (total == 40) {
                            validCombinations.add(List.of(s1, s2, s3));
                        }
                    }
                }
            }

            if (validCombinations.isEmpty()) {
                throw new AppException(ErrorCode.INVALID_KEY) {
                    @Override
                    public String getMessage() {
                        return "Khong tim duoc to hop Reading du 40 cau de tao full test.";
                    }
                };
            }

            Collections.shuffle(validCombinations);
            List<Stimulus> chosen = validCombinations.get(0);
            for (int i = 0; i < chosen.size(); i++) {
                TestStructure ts = new TestStructure();
                ts.setTest(saved);
                ts.setStimulus(chosen.get(i));
                ts.setSection(i + 1);
                testStructureRepository.save(ts);
            }
        } else {
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
    public FullTestResponse getFullTestById(Integer id) {
        Test test = testRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.TEST_NOT_FOUND));
        if (test.getTestMode() != null && test.getTestMode() != TestMode.FULL_TEST) {
            throw new AppException(ErrorCode.INVALID_KEY);
        }
        return toFullTestResponse(test);
    }

    @Override
    @Transactional
    public FullTestResponse updateFullTest(Integer id, CreateFullTestRequest request) {
        Test test = testRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.TEST_NOT_FOUND));
        if (test.getTestMode() != TestMode.FULL_TEST) {
            throw new AppException(ErrorCode.INVALID_KEY);
        }

        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            test.setTitle(request.getTitle());
        }
        if (request.getSkill() != null && !request.getSkill().isBlank()) {
            test.setSkill(parseSkill(request.getSkill()));
        }
        if (request.getTestType() != null && !request.getTestType().isBlank()) {
            test.setTestType(parseTestType(request.getTestType()));
        }
        if (request.getDuration() != null) {
            test.setDuration(request.getDuration());
        }
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            test.setStatus(parseStatus(request.getStatus()));
        }

        Test saved = testRepository.save(test);

        if (request.getStimulusIds() != null && !request.getStimulusIds().isEmpty()) {
            Skill effectiveSkill = saved.getSkill();
            int requiredSections = SECTION_COUNT.getOrDefault(effectiveSkill, 3);
            if (request.getStimulusIds().size() != requiredSections) {
                throw new AppException(ErrorCode.INVALID_KEY);
            }

            List<Stimulus> selectedStimuli = fetchStimuliByIds(request.getStimulusIds());
            validateReadingQuestionTotal(effectiveSkill, selectedStimuli);

            testStructureRepository.deleteByTestId(id);
            int order = 0;
            for (Stimulus stimulus : selectedStimuli) {
                TestStructure ts = new TestStructure();
                ts.setTest(saved);
                ts.setStimulus(stimulus);
                ts.setSection(order + 1);
                order++;
                testStructureRepository.save(ts);
            }
        }

        return toFullTestResponse(saved);
    }

    @Override
    public Map<Integer, List<StimulusOption>> getAvailableStimuli(String skill) {
        Skill skillEnum = parseSkill(skill);
        List<Test> practiceTests = testRepository.findByTestModeAndSkill(TestMode.PRACTICE, skillEnum);

        Map<Integer, Map<Integer, StimulusOption>> groupedBySection = new LinkedHashMap<>();

        for (Test practiceTest : practiceTests) {
            List<TestStructure> structures = testStructureRepository.findByTestId(practiceTest.getId());
            for (TestStructure ts : structures) {
                int section = resolveSection(practiceTest, ts);
                Stimulus stimulus = ts.getStimulus();
                int questionCount = countQuestions(stimulus);

                String title = practiceTest.getTitle() != null
                                && !practiceTest.getTitle().isBlank()
                        ? practiceTest.getTitle()
                        : (stimulus.getTitle() != null && !stimulus.getTitle().isBlank()
                                ? stimulus.getTitle()
                                : "(Khong co tieu de)");

                StimulusOption option = StimulusOption.builder()
                        .stimulusId(stimulus.getId())
                        .testId(practiceTest.getId())
                        .title(title)
                        .questionCount(questionCount)
                        .build();

                Map<Integer, StimulusOption> sectionOptions =
                        groupedBySection.computeIfAbsent(section, k -> new LinkedHashMap<>());
                StimulusOption existing = sectionOptions.get(stimulus.getId());

                if (existing == null || titleScore(option.getTitle()) > titleScore(existing.getTitle())) {
                    sectionOptions.put(stimulus.getId(), option);
                }
            }
        }

        Map<Integer, List<StimulusOption>> result = new LinkedHashMap<>();
        for (Map.Entry<Integer, Map<Integer, StimulusOption>> entry : groupedBySection.entrySet()) {
            result.put(entry.getKey(), new ArrayList<>(entry.getValue().values()));
        }

        return result;
    }

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
                    Stimulus stimulus = ts.getStimulus();
                    String title =
                            stimulus.getTitle() != null && !stimulus.getTitle().isBlank()
                                    ? stimulus.getTitle()
                                    : "(Khong co tieu de)";
                    SourceTestInfo source = resolveSourcePracticeTest(stimulus.getId(), ts.getSection());

                    return FullTestResponse.StimulusInfo.builder()
                            .id(stimulus.getId())
                            .section(ts.getSection())
                            .title(title)
                            .questionCount(countQuestions(stimulus))
                            .testId(source != null ? source.testId() : null)
                            .testTitle(source != null ? source.title() : null)
                            .build();
                })
                .collect(Collectors.toList());

        return FullTestResponse.builder()
                .id(test.getId())
                .title(test.getTitle())
                .skill(test.getSkill() != null ? test.getSkill().name() : null)
                .testType(test.getTestType() != null ? test.getTestType().name() : null)
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

    private TestType parseTestType(String testType) {
        try {
            return TestType.valueOf(testType.toUpperCase());
        } catch (Exception e) {
            throw new AppException(ErrorCode.INVALID_KEY);
        }
    }

    private PublishStatus parseStatus(String status) {
        try {
            return PublishStatus.valueOf(status.toUpperCase());
        } catch (Exception e) {
            throw new AppException(ErrorCode.INVALID_KEY);
        }
    }

    private List<Stimulus> fetchStimuliByIds(List<Integer> stimulusIds) {
        List<Stimulus> result = new ArrayList<>();
        for (Integer stimulusId : stimulusIds) {
            Stimulus stimulus = stimulusRepository
                    .findById(stimulusId)
                    .orElseThrow(() -> new AppException(ErrorCode.STIMULUS_NOT_FOUND));
            result.add(stimulus);
        }
        return result;
    }

    private int countQuestions(Stimulus stimulus) {
        return stimulus.getQuestionGroups().stream()
                .mapToInt(group -> group.getQuestions().size())
                .sum();
    }

    private void validateReadingQuestionTotal(Skill skill, List<Stimulus> stimuli) {
        if (skill != Skill.READING) {
            return;
        }

        int totalQuestions = stimuli.stream().mapToInt(this::countQuestions).sum();
        if (totalQuestions != 40) {
            throw new AppException(ErrorCode.INVALID_KEY) {
                @Override
                public String getMessage() {
                    return "Reading full test bat buoc co tong 40 cau.";
                }
            };
        }
    }

    private SourceTestInfo resolveSourcePracticeTest(Integer stimulusId, Integer section) {
        return testStructureRepository.findByStimulusId(stimulusId).stream()
                .filter(ts -> ts.getTest() != null && ts.getTest().getTestMode() == TestMode.PRACTICE)
                .filter(ts -> section == null || ts.getSection() == null || section.equals(ts.getSection()))
                .max(Comparator.comparingInt(ts -> titleScore(ts.getTest().getTitle())))
                .map(ts -> new SourceTestInfo(ts.getTest().getId(), ts.getTest().getTitle()))
                .orElse(null);
    }

    private int titleScore(String title) {
        if (title == null || title.isBlank()) {
            return 0;
        }

        String normalized = title.trim().toLowerCase();
        boolean isGeneric = normalized.matches("^(passage|section|part|task)\\s*\\d+$");
        return isGeneric ? 1 : 2;
    }

    private int resolveSection(Test practiceTest, TestStructure structure) {
        if (practiceTest.getSection() != null) {
            return practiceTest.getSection();
        }
        if (structure.getSection() != null) {
            return structure.getSection();
        }
        return 1;
    }

    private record SourceTestInfo(Integer testId, String title) {}
}
