package io.gsp26se16.moni.authentication.mapper;


import io.gsp26se16.moni.authentication.dto.request.RegisterRequest;
import io.gsp26se16.moni.authentication.entity.UserCredentials;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserCredentialsMapper {
    UserCredentials toUserCredentials(RegisterRequest request);
}
