package io.gsp26se16.moni.placement.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.gsp26se16.moni.common.enumeration.PublishStatus;
import io.gsp26se16.moni.common.enumeration.Skill;
import io.gsp26se16.moni.common.exception.AppException;
import io.gsp26se16.moni.common.exception.ErrorCode;
import io.gsp26se16.moni.content.entity.Test;
import io.gsp26se16.moni.content.repository.TestRepository;
import io.gsp26se16.moni.placement.dto.request.PlacementConfigRequest;
import io.gsp26se16.moni.placement.dto.response.PlacementConfigResponse;
import io.gsp26se16.moni.placement.entity.PlacementConfig;
import io.gsp26se16.moni.placement.repository.PlacementConfigRepository;
import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class PlacementConfigServiceImpl implements PlacementConfigService {

    private final PlacementConfigRepository placementConfigRepository;
    private final TestRepository testRepository;

    @Override
    @Transactional(readOnly = true)
    public List<PlacementConfigResponse> listAll() {
        return placementConfigRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public PlacementConfigResponse create(PlacementConfigRequest request) {
        Map<Skill, Test> tests = validateAndLoadTests(request);

        PlacementConfig config = PlacementConfig.builder()
                .name(request.getName())
                .readingTest(tests.get(Skill.READING))
                .listeningTest(tests.get(Skill.LISTENING))
                .writingTest(tests.get(Skill.WRITING))
                .speakingTest(tests.get(Skill.SPEAKING))
                .isActive(false)
                .build();

        config = placementConfigRepository.save(config);
        return toResponse(config);
    }

    @Override
    public PlacementConfigResponse update(Integer id, PlacementConfigRequest request) {
        PlacementConfig config = placementConfigRepository
                .findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PLACEMENT_CONFIG_NOT_FOUND));

        Map<Skill, Test> tests = validateAndLoadTests(request);

        config.setName(request.getName());
        config.setReadingTest(tests.get(Skill.READING));
        config.setListeningTest(tests.get(Skill.LISTENING));
        config.setWritingTest(tests.get(Skill.WRITING));
        config.setSpeakingTest(tests.get(Skill.SPEAKING));

        config = placementConfigRepository.save(config);
        return toResponse(config);
    }

    @Override
    public void activate(Integer id) {
        PlacementConfig config = placementConfigRepository
                .findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PLACEMENT_CONFIG_NOT_FOUND));

        placementConfigRepository.deactivateAll();
        config.setIsActive(true);
        placementConfigRepository.save(config);
    }

    @Override
    public void delete(Integer id) {
        PlacementConfig config = placementConfigRepository
                .findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PLACEMENT_CONFIG_NOT_FOUND));

        if (Boolean.TRUE.equals(config.getIsActive())) {
            throw new AppException(ErrorCode.PLACEMENT_CONFIG_CANNOT_DELETE_ACTIVE);
        }

        placementConfigRepository.delete(config);
    }

    // --- Helpers ---

    private Map<Skill, Test> validateAndLoadTests(PlacementConfigRequest request) {
        Test reading = validateTest(request.getReadingTestId(), Skill.READING);
        Test listening = validateTest(request.getListeningTestId(), Skill.LISTENING);
        Test writing = validateTest(request.getWritingTestId(), Skill.WRITING);
        Test speaking = validateTest(request.getSpeakingTestId(), Skill.SPEAKING);

        return Map.of(
                Skill.READING, reading,
                Skill.LISTENING, listening,
                Skill.WRITING, writing,
                Skill.SPEAKING, speaking);
    }

    private Test validateTest(Integer testId, Skill expectedSkill) {
        Test test = testRepository.findById(testId).orElseThrow(() -> new AppException(ErrorCode.TEST_NOT_FOUND));

        if (test.getSkill() != expectedSkill) {
            throw new AppException(ErrorCode.PLACEMENT_CONFIG_INVALID_TEST);
        }

        if (test.getStatus() != PublishStatus.PUBLISHED) {
            throw new AppException(ErrorCode.PLACEMENT_CONFIG_INVALID_TEST);
        }

        return test;
    }

    private PlacementConfigResponse toResponse(PlacementConfig config) {
        return PlacementConfigResponse.builder()
                .id(config.getId())
                .name(config.getName())
                .readingTestId(config.getReadingTest().getId())
                .readingTestTitle(config.getReadingTest().getTitle())
                .listeningTestId(config.getListeningTest().getId())
                .listeningTestTitle(config.getListeningTest().getTitle())
                .writingTestId(config.getWritingTest().getId())
                .writingTestTitle(config.getWritingTest().getTitle())
                .speakingTestId(config.getSpeakingTest().getId())
                .speakingTestTitle(config.getSpeakingTest().getTitle())
                .isActive(config.getIsActive())
                .createdAt(config.getCreatedAt())
                .build();
    }
}
