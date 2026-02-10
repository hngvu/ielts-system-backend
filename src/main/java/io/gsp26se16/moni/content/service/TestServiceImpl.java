package io.gsp26se16.moni.content.service;

import io.gsp26se16.moni.content.dto.request.TestImportRequest;
import io.gsp26se16.moni.content.entity.*;
import io.gsp26se16.moni.content.repository.QuestionTypeRepository;
import io.gsp26se16.moni.content.repository.StimulusRepository;
import io.gsp26se16.moni.content.repository.TestRepository;
import io.gsp26se16.moni.content.repository.TestStructureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TestServiceImpl implements TestService {

    private final TestRepository testRepository;
    private final StimulusRepository stimulusRepository;
    private final TestStructureRepository testStructureRepository;
    private final QuestionTypeRepository questionTypeRepository;

    @Override
    @Transactional // Quan trọng: Nếu lỗi ở bất kỳ bước nào, Rollback sạch sẽ
    public Integer importTest(TestImportRequest request) {
        // BƯỚC 1: Tạo Test (Cái vỏ đề thi)
        Test test = new Test();
        test.setTitle(request.getTitle());
        test.setDescription(request.getDescription());
        test.setSkill(request.getSkill());
        test.setTestType(request.getTestType());

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
}
