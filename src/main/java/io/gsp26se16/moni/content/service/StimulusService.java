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

    public void updateStimulus(Integer id, String content, String mediaUrl, Object transcript);

    public Page<StimulusResponse> getAllStimuli(String keyword, Skill skill, Pageable pageable);

    List<Map<String, Object>> transcribeAndSave(Integer stimulusId);

    List<Map<String, Object>> getTranscript(Integer stimulusId);

    List<Map<String, Object>> transcribeByUrl(String audioUrl);
}
