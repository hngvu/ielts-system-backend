package io.gsp26se16.moni.tag.service;

import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.gsp26se16.moni.common.exception.AppException;
import io.gsp26se16.moni.common.exception.ErrorCode;
import io.gsp26se16.moni.content.entity.Question;
import io.gsp26se16.moni.content.entity.Stimulus;
import io.gsp26se16.moni.content.entity.Test;
import io.gsp26se16.moni.content.repository.QuestionRepository;
import io.gsp26se16.moni.content.repository.StimulusRepository;
import io.gsp26se16.moni.content.repository.TestRepository;
import io.gsp26se16.moni.tag.dto.request.TagAssignRequest;
import io.gsp26se16.moni.tag.dto.request.TagRequest;
import io.gsp26se16.moni.tag.dto.response.TagResponse;
import io.gsp26se16.moni.tag.entity.Tag;
import io.gsp26se16.moni.tag.entity.TagType;
import io.gsp26se16.moni.tag.mapper.TagMapper;
import io.gsp26se16.moni.tag.repository.TagRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TagServiceImpl implements TagService {

    private final TagRepository tagRepository;
    private final TagMapper tagMapper;
    private final QuestionRepository questionRepository;
    private final StimulusRepository stimulusRepository;
    private final TestRepository testRepository;

    @Override
    @Transactional
    public TagResponse createTag(TagRequest request) {
        // 1. Check trùng code
        if (tagRepository.existsByCode(request.getCode())) {
            throw new AppException(ErrorCode.TAG_EXISTED);
        }

        // 2. Map & Save
        Tag tag = tagMapper.toEntity(request);
        Tag savedTag = tagRepository.save(tag);

        return tagMapper.toResponse(savedTag);
    }

    @Override
    public List<TagResponse> getTags(TagType type, String keyword) {
        List<Tag> tags;

        // Logic lọc đơn giản
        if (type != null) {
            tags = tagRepository.findByType(type);
        } else if (keyword != null && !keyword.isEmpty()) {
            tags = tagRepository.findByNameContainingIgnoreCase(keyword);
        } else {
            tags = tagRepository.findAll();
        }

        return tags.stream().map(tagMapper::toResponse).collect(Collectors.toList());
    }

    @Override
    public TagResponse getTagById(Integer id) {
        Tag tag = tagRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.TAG_NOT_FOUND));
        return tagMapper.toResponse(tag);
    }

    @Override
    @Transactional
    public TagResponse updateTag(Integer id, TagRequest request) {
        Tag tag = tagRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.TAG_NOT_FOUND));

        // 1. Check trùng code (trừ chính nó ra)
        if (tagRepository.existsByCodeAndIdNot(request.getCode(), id)) {
            throw new AppException(ErrorCode.TAG_EXISTED);
        }

        // 2. Update & Save
        tagMapper.updateTagFromRequest(request, tag);
        return tagMapper.toResponse(tagRepository.save(tag));
    }

    @Override
    @Transactional
    public void deleteTag(Integer id) {
        if (!tagRepository.existsById(id)) {
            throw new AppException(ErrorCode.TAG_NOT_FOUND);
        }
        // TODO: Về sau cần check xem Tag có đang được dùng trong Bài thi không trước khi xóa
        tagRepository.deleteById(id);
    }

    @Override
    @Transactional
    public void assignTagsToQuestion(Integer questionId, TagAssignRequest request) {
        Question question = questionRepository
                .findById(questionId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy Câu hỏi với ID: " + questionId));

        // Lấy danh sách Tag từ DB dựa trên list IDs gửi lên
        List<Tag> tags = tagRepository.findAllById(request.getTagIds());
        if (tags.size() != request.getTagIds().size()) {
            throw new RuntimeException("Một hoặc nhiều Tag không tồn tại trong hệ thống!");
        }

        // Ghi đè list Tag mới vào (Nhờ Set và Hibernate, nó sẽ tự động tính toán cái nào cần thêm/xóa trong bảng trung
        // gian)
        question.setTags(new HashSet<>(tags));
        questionRepository.save(question);
    }

    @Override
    @Transactional
    public void assignTagsToStimulus(Integer stimulusId, TagAssignRequest request) {
        Stimulus stimulus = stimulusRepository
                .findById(stimulusId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy Đoạn văn/Audio với ID: " + stimulusId));

        List<Tag> tags = tagRepository.findAllById(request.getTagIds());
        if (tags.size() != request.getTagIds().size()) {
            throw new RuntimeException("Một hoặc nhiều Tag không tồn tại trong hệ thống!");
        }

        stimulus.setTags(new HashSet<>(tags));
        stimulusRepository.save(stimulus);
    }

    @Override
    @Transactional
    public void assignTagsToTest(Integer testId, TagAssignRequest request) {
        Test test = testRepository
                .findById(testId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy Đề thi với ID: " + testId));

        List<Tag> tags = tagRepository.findAllById(request.getTagIds());
        if (tags.size() != request.getTagIds().size()) {
            throw new RuntimeException("Một hoặc nhiều Tag không tồn tại trong hệ thống!");
        }

        test.setTags(new HashSet<>(tags));
        testRepository.save(test);
    }
}
