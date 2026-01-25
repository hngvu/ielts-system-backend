package io.gsp26se16.moni.authentication.service;


import io.gsp26se16.moni.authentication.dto.request.ChangePassWordRequest;
import io.gsp26se16.moni.authentication.dto.request.RegisterRequest;
import io.gsp26se16.moni.authentication.dto.response.UserProfileResponse;

public interface UserCredentialService {
    UserProfileResponse register(RegisterRequest request);

    void banUser(String userId);

    void changePassword(String id, ChangePassWordRequest request);
}
