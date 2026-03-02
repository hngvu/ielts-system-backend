package io.gsp26se16.moni.content.service;

import io.gsp26se16.moni.common.enumeration.Skill;
import io.gsp26se16.moni.content.dto.request.StimulusCreateRequest;
import io.gsp26se16.moni.content.dto.response.StimulusResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface StimulusService {
    public Integer createStimulus(StimulusCreateRequest request);
    public Page<StimulusResponse> getAllStimuli(String keyword, Skill skill, Pageable pageable);
}
