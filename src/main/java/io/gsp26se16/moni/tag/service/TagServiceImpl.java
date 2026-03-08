package io.gsp26se16.moni.tag.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.gsp26se16.moni.common.exception.AppException;
import io.gsp26se16.moni.common.exception.ErrorCode;
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
}