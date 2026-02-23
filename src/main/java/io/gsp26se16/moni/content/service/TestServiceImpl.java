package io.gsp26se16.moni.content.service;

import io.gsp26se16.moni.common.enumeration.Skill;
import io.gsp26se16.moni.common.exception.AppException;
import io.gsp26se16.moni.common.exception.ErrorCode;
import io.gsp26se16.moni.content.dto.request.TestImportRequest;
import io.gsp26se16.moni.content.dto.response.TestDetailResponse;
import io.gsp26se16.moni.content.dto.response.TestResponse;
import io.gsp26se16.moni.content.entity.*;
import io.gsp26se16.moni.content.repository.QuestionTypeRepository;
import io.gsp26se16.moni.content.repository.StimulusRepository;
import io.gsp26se16.moni.content.repository.TestRepository;
import io.gsp26se16.moni.content.repository.TestStructureRepository;
import io.gsp26se16.moni.tag.entity.Tag;
import io.gsp26se16.moni.tag.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TestServiceImpl implements TestService {

    private final TestRepository testRepository;
    private final StimulusRepository stimulusRepository;
    private final TestStructureRepository testStructureRepository;
    private final QuestionTypeRepository questionTypeRepository;
    private final TagRepository tagRepository;

    @Override
    @Transactional // Quan trọng: Nếu lỗi ở bất kỳ bước nào, Rollback sạch sẽ
    public Integer importTest(TestImportRequest request) {
        // BƯỚC 1: Tạo Test (Cái vỏ đề thi)
        Test test = new Test();
        test.setTitle(request.getTitle());
        test.setDescription(request.getDescription());
        test.setSkill(request.getSkill());
        test.setTestType(request.getTestType());

        // --- LOGIC XỬ LÝ TAG (MỚI) ---
        if (request.getTagIds() != null && !request.getTagIds().isEmpty()) {
            List<Tag> tags = tagRepository.findAllById(request.getTagIds());
            if (tags.size() < request.getTagIds().size()) {
                // Tùy chọn: Báo lỗi nếu ID tag không tồn tại
                // throw new AppException(ErrorCode.TAG_NOT_FOUND);
            }
            test.setTags(new HashSet<>(tags));
        }

        // Lưu Test trước để có ID
        Test savedTest = testRepository.save(test);

        // BƯỚC 2: Duyệt qua danh sách Ngữ liệu (Stimulus)
        if (request.getStimuli() != null) {
            for (TestImportRequest.StimulusRequest stimReq : request.getStimuli()) {

                // 2.1 Map thông tin Stimulus
                Stimulus stimulus = new Stimulus();
                stimulus.setTitle(stimReq.getTitle());
                stimulus.setContent(stimReq.getContent()); // Nội dung bài đọc HTML/Text
                stimulus.setMediaUrl(stimReq.getMediaUrl()); // Link Audio
                stimulus.setSection(stimReq.getSection());
                stimulus.setSkill(request.getSkill());
                stimulus.setTestType(request.getTestType());

                // TODO: Set User tạo (lấy từ SecurityContext sau này)
                // stimulus.setCreatedBy(currentUser);

                // 2.2 Map QuestionGroups (Nhóm câu hỏi)
                if (stimReq.getQuestionGroups() != null) {
                    List<QuestionGroup> groups = new ArrayList<>();

                    for (TestImportRequest.QuestionGroupRequest groupReq : stimReq.getQuestionGroups()) {
                        QuestionGroup group = new QuestionGroup();
                        group.setInstruction(groupReq.getInstruction());
                        group.setStimulus(stimulus); // 🔥 Gán cha (Quan trọng)

                        // Tìm QuestionType trong DB (Bắt buộc phải có từ DataInitializer)
                        QuestionType type = questionTypeRepository.findByCode(groupReq.getQuestionTypeCode())
                                .orElseThrow(() -> new RuntimeException("Question Type not found: " + groupReq.getQuestionTypeCode()));
                        group.setQuestionType(type);

                        // 2.3 Map Questions (Câu hỏi)
                        if (groupReq.getQuestions() != null) {
                            List<Question> questions = new ArrayList<>();
                            for (TestImportRequest.QuestionRequest qReq : groupReq.getQuestions()) {
                                Question question = new Question();
                                question.setContent(qReq.getContent());
                                question.setPosition(qReq.getPosition());
                                question.setMetadata(qReq.getMetadata());       // Map JSONB
                                question.setExplanation(qReq.getExplanation()); // Map JSONB
                                question.setQuestionGroup(group); // 🔥 Gán cha

                                // 2.4 Map Options (Đáp án A,B,C,D)
                                if (qReq.getOptions() != null) {
                                    List<QuestionOption> options = new ArrayList<>();
                                    for (TestImportRequest.OptionRequest optReq : qReq.getOptions()) {
                                        QuestionOption option = new QuestionOption();
                                        option.setLabel(optReq.getLabel());
                                        option.setContent(optReq.getContent());
                                        option.setCorrect(optReq.getIsCorrect()); // Content có quyền lưu đáp án đúng
                                        option.setQuestion(question); // 🔥 Gán cha
                                        option.setQuestionGroup(group);
                                        options.add(option);
                                    }
                                    question.setOptions(options); // Setter list options
                                }
                                questions.add(question);
                            }
                            group.setQuestions(questions); // Setter list questions
                        }
                        groups.add(group);
                    }
                    stimulus.setQuestionGroups(groups); // Setter list groups
                }

                // 2.5 Lưu Stimulus (Nhờ CascadeType.ALL, nó sẽ lưu tuốt luốt Group -> Question -> Option)
                Stimulus savedStimulus = stimulusRepository.save(stimulus);

                // BƯỚC 3: Tạo liên kết TestStructure
                // Đây là bảng trung gian nối Test và Stimulus
                TestStructure structure = new TestStructure();
                structure.setTest(savedTest);
                structure.setStimulus(savedStimulus);

                testStructureRepository.save(structure);
            }
        }

        return savedTest.getId();
    }

    @Override
    public Page<TestResponse> getTests(String keyword, Skill skill, Pageable pageable) {
        // 1. Gọi Repo để lấy danh sách Entity (Đã phân trang)
        Page<Test> testPage = testRepository.searchTests(keyword, skill, pageable);

        // 2. Chuyển đổi từ Entity (Test) sang DTO (TestResponse)
        return testPage.map(test -> TestResponse.builder()
                .id(test.getId())
                .title(test.getTitle())
                .description(test.getDescription())
                .skill(test.getSkill())
                .testType(test.getTestType())
                // .duration(test.getDuration()) // Mở comment nếu Entity Test có field này
                .createdAt(LocalDateTime.now()) // Tạm thời để now, sau này lấy test.getCreatedAt()

                // Map Tags: Lấy tên tag từ Set<Tag>
                .tags(test.getTags().stream()
                        .map(Tag::getName)
                        .toList())
                .build());
    }

    @Override
    @Transactional(readOnly = true) // Tối ưu hiệu năng cho thao tác đọc
    public TestDetailResponse getTestDetail(Integer id) {
        // 1. Tìm Test
        Test test = testRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.TEST_NOT_FOUND)); // Nhớ định nghĩa ErrorCode này

        // 2. Tìm danh sách Stimulus thông qua bảng TestStructure
        // (Lý do: Stimulus không lưu test_id trực tiếp)
        List<TestStructure> structures = testStructureRepository.findByTestId(id);

        // Sắp xếp theo thứ tự section (Part 1 -> Part 2 -> ...)
        structures.sort(Comparator.comparingInt(ts -> ts.getStimulus().getSection()));

        // 3. Map sang DTO (Hơi dài nhưng cấu trúc rõ ràng)
        List<TestDetailResponse.StimulusDetail> stimulusDTOs = structures.stream()
                .map(ts -> {
                    Stimulus s = ts.getStimulus();
                    return TestDetailResponse.StimulusDetail.builder()
                            .id(s.getId())
                            .title(s.getTitle())
                            .content(s.getContent())
                            .mediaUrl(s.getMediaUrl())
                            .section(s.getSection()) // Hoặc lấy section từ TestStructure nếu bạn lưu ở đó
                            // Map Question Groups
                            .questionGroups(s.getQuestionGroups().stream().map(g ->
                                    TestDetailResponse.QuestionGroupDetail.builder()
                                            .id(g.getId())
                                            .instruction(g.getInstruction())
                                            .questionTypeCode(g.getQuestionType().getCode())
                                            // Map Questions
                                            .questions(g.getQuestions().stream().map(q ->
                                                    TestDetailResponse.QuestionDetail.builder()
                                                            .id(q.getId())
                                                            .content(q.getContent())
                                                            .position(q.getPosition())
                                                            .metadata(q.getMetadata())
                                                            .explanation(q.getExplanation())
                                                            // Map Options
                                                            .options(q.getOptions().stream().map(o ->
                                                                    TestDetailResponse.OptionDetail.builder()
                                                                            .id(o.getId())
                                                                            .label(o.getLabel())
                                                                            .content(o.getContent())
                                                                            .isCorrect(o.isCorrect())
                                                                            .build()
                                                            ).collect(Collectors.toList()))
                                                            .build()
                                            ).collect(Collectors.toList()))
                                            .build()
                            ).collect(Collectors.toList()))
                            .build();
                }).collect(Collectors.toList());

        // 4. Trả về kết quả cuối cùng
        return TestDetailResponse.builder()
                .id(test.getId())
                .title(test.getTitle())
                .description(test.getDescription())
                .skill(test.getSkill())
                .testType(test.getTestType())
                .tags(test.getTags().stream().map(Tag::getName).toList())
                .stimuli(stimulusDTOs)
                .build();
    }
}
