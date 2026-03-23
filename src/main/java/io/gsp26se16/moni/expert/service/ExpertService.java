package io.gsp26se16.moni.expert.service;

import java.util.List;

import io.gsp26se16.moni.expert.dto.CreateExpertRequest;
import io.gsp26se16.moni.expert.dto.ExpertProfileResponse;
import io.gsp26se16.moni.expert.enumeration.ExpertSpecialization;
import io.gsp26se16.moni.expert.enumeration.ExpertStatus;

public interface ExpertService {
    List<ExpertProfileResponse> listExperts(ExpertSpecialization filter);

    ExpertProfileResponse getExpert(Integer id);

    ExpertProfileResponse createExpert(CreateExpertRequest request);

    void updateStatus(Integer id, ExpertStatus status);

    ExpertProfileResponse getMyProfile(String credentialId);

    void updateMyStatus(String credentialId, ExpertStatus status);

    void deleteExpert(Integer id);
}
