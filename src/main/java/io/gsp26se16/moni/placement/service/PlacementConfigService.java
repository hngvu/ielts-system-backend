package io.gsp26se16.moni.placement.service;

import java.util.List;

import io.gsp26se16.moni.placement.dto.request.PlacementConfigRequest;
import io.gsp26se16.moni.placement.dto.response.PlacementConfigResponse;

public interface PlacementConfigService {

    List<PlacementConfigResponse> listAll();

    PlacementConfigResponse create(PlacementConfigRequest request);

    PlacementConfigResponse update(Integer id, PlacementConfigRequest request);

    void activate(Integer id);

    void delete(Integer id);
}
