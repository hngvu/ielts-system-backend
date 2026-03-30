package io.gsp26se16.moni.content.service;

import java.util.List;
import java.util.Map;

import io.gsp26se16.moni.content.dto.request.AutoFullTestRequest;
import io.gsp26se16.moni.content.dto.request.CreateFullTestRequest;
import io.gsp26se16.moni.content.dto.response.FullTestResponse;
import io.gsp26se16.moni.content.dto.response.StimulusOption;

public interface FullTestService {
    List<FullTestResponse> listFullTests();

    FullTestResponse createFullTest(CreateFullTestRequest request);

    FullTestResponse autoGenerateFullTest(AutoFullTestRequest request);

    void deleteFullTest(Integer id);

    FullTestResponse getFullTestById(Integer id);

    FullTestResponse updateFullTest(Integer id, CreateFullTestRequest request);

    Map<Integer, List<StimulusOption>> getAvailableStimuli(String skill);
}
