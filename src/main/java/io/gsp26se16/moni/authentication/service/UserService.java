package io.gsp26se16.moni.authentication.service;



import io.gsp26se16.moni.authentication.dto.request.UpdateProfileRequest;
import io.gsp26se16.moni.authentication.dto.response.UserProfileResponse;

import java.util.List;

public interface UserService {

    List<UserProfileResponse> getAll();

    UserProfileResponse getUserProfile(String id);

    UserProfileResponse getMe();

    UserProfileResponse updateProfile(UpdateProfileRequest request);
}
