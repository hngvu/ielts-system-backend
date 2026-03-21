package io.gsp26se16.moni.authentication.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import io.gsp26se16.moni.authentication.dto.request.RegisterRequest;
import io.gsp26se16.moni.authentication.dto.response.UserProfileResponse;
import io.gsp26se16.moni.authentication.entity.Users;

@Mapper(componentModel = "spring")
public interface UserMapper {
    @Mapping(source = "fullName", target = "full_name")
    Users toUser(RegisterRequest request);

    @Mapping(source = "credential.email", target = "email")
    @Mapping(source = "full_name", target = "full_name")
    @Mapping(source = "credential.role", target = "role")
    UserProfileResponse toUserProfileResponse(Users user);
}
