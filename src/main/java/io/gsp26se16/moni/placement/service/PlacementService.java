package io.gsp26se16.moni.placement.service;

import io.gsp26se16.moni.placement.dto.request.PlacementSelfAssessRequest;
import io.gsp26se16.moni.placement.dto.request.PlacementSubmitRequest;
import io.gsp26se16.moni.placement.dto.response.PlacementResultResponse;
import io.gsp26se16.moni.placement.dto.response.PlacementTestResponse;

public interface PlacementService {

    PlacementTestResponse generate();

    PlacementResultResponse submit(PlacementSubmitRequest request);

    PlacementResultResponse selfAssess(PlacementSelfAssessRequest request);

    PlacementResultResponse getResult();

    void reset();
}
