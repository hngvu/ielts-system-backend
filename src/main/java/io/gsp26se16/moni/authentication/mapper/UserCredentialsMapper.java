package io.gsp26se16.moni.authentication.mapper;

import org.mapstruct.Mapper;

import io.gsp26se16.moni.authentication.dto.request.RegisterRequest;
import io.gsp26se16.moni.authentication.entity.UserCredentials;

@Mapper(componentModel = "spring")
public interface UserCredentialsMapper {
    UserCredentials toUserCredentials(RegisterRequest request);
}
