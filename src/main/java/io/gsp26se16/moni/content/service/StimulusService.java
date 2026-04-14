package io.gsp26se16.moni.content.service;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import io.gsp26se16.moni.common.enumeration.Skill;
import io.gsp26se16.moni.content.dto.request.StimulusCreateRequest;
import io.gsp26se16.moni.content.dto.response.StimulusResponse;

public interface StimulusService {
    public Integer createStimulus(StimulusCreateRequest request);

    public StimulusResponse updateStimulus(Integer id, String content, String mediaUrl, Object transcript);

    public Page<StimulusResponse> getAllStimuli(String keyword, Skill skill, Pageable pageable);

    List<Map<String, Object>> transcribeAndSave(Integer stimulusId);

    List<Map<String, Object>> getTranscript(Integer stimulusId);

    List<Map<String, Object>> transcribeByUrl(String audioUrl);

    /**
     * Analyze chart image using Vision AI and cache result in Stimulus.visonAnalysisResult.
     * Called by Admin when creating/editing Writing Task 1 tests.
     */
    Map<String, Object> analyzeChart(Integer stimulusId, byte[] imageBytes);

    /**
     * Update the visonAnalysisResult JSON directly (Admin manual correction).
     */
    void updateVisonAnalysisResult(Integer stimulusId, Map<String, Object> visonAnalysisResult);

    /**
     * Get the stored visonAnalysisResult for a stimulus.
     */
    Map<String, Object> getVisonAnalysisResult(Integer stimulusId);
}
