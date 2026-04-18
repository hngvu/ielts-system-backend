package io.gsp26se16.moni.placement.service;

import io.gsp26se16.moni.placement.dto.request.AiRecommendRequest;
import io.gsp26se16.moni.placement.dto.response.AiRecommendResponse;

public interface PlacementAiService {

    AiRecommendResponse recommend(AiRecommendRequest request);
}
