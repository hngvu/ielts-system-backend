package io.gsp26se16.moni.authentication.service;


import io.gsp26se16.moni.authentication.dto.request.UpdateProfileRequest;
import io.gsp26se16.moni.authentication.dto.response.UserProfileResponse;
import io.gsp26se16.moni.authentication.entity.UserCredentials;
import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.authentication.mapper.UserMapper;
import io.gsp26se16.moni.authentication.repository.UserCredentialsRepository;
import io.gsp26se16.moni.authentication.repository.UsersRepository;
import io.gsp26se16.moni.common.exception.AppException;
import io.gsp26se16.moni.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {
    private final UsersRepository usersRepository;
    private final UserCredentialsRepository userCredentialsRepository;
    private final UserMapper userMapper;

    @Override
    public List<UserProfileResponse> getAll() {
        return usersRepository.findAll().stream()
                .map(userMapper::toUserProfileResponse)
                .collect(Collectors.toList());
    }

    @Override
    public UserProfileResponse getUserProfile(String id) {
        return userCredentialsRepository
                .findById(id)
                .map(UserCredentials::getUser)
                .map(userMapper::toUserProfileResponse)
                .orElse(null);
    }

    @Override
    public UserProfileResponse getMe() {
        var context = SecurityContextHolder.getContext();
        var authentication = context.getAuthentication();

        // Extract userId from JWT claims
        String userId = null;
        if (authentication.getPrincipal() instanceof org.springframework.security.oauth2.jwt.Jwt jwt) {
            userId = jwt.getClaim("userId");
            log.info("Extracted userId from JWT: {}", userId);
        }

        if (userId == null) {
            log.error("UserId is null from JWT");
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        final String credentialId = userId;
        log.info("Looking for credentials with ID: {}", credentialId);
        UserCredentials credentials = userCredentialsRepository
                .findById(credentialId)
                .orElseThrow(() -> {
                    log.error("Credentials not found with ID: {}", credentialId);
                    return new AppException(ErrorCode.USER_NOT_EXISTED);
                });

        Users user = credentials.getUser();
        if (user == null) {
            log.error("User not linked to credentials ID: {}", userId);
            throw new AppException(ErrorCode.USER_NOT_EXISTED);
        }

        log.info("Found user: {}", user.getId());
        return userMapper.toUserProfileResponse(user);
    }

    @Override
    public UserProfileResponse updateProfile(UpdateProfileRequest request) {
        var context = SecurityContextHolder.getContext();
        var authentication = context.getAuthentication();

        // Extract userId from JWT claims
        String userId = null;
        if (authentication.getPrincipal() instanceof org.springframework.security.oauth2.jwt.Jwt jwt) {
            userId = jwt.getClaim("userId");
        }

        if (userId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        UserCredentials credentials = userCredentialsRepository
                .findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        Users user = credentials.getUser();
        if (user == null) {
            throw new AppException(ErrorCode.USER_NOT_EXISTED);
        }
        updateUserInfo(user, request);

        return userMapper.toUserProfileResponse(usersRepository.save(user));
    }

    private void updateUserInfo(Users user, UpdateProfileRequest request) {
        if (request.getFullName() != null) user.setFull_name(request.getFullName());

        if (request.getAvatarUrl() != null) user.setAvatar_url(request.getAvatarUrl());

        if (request.getPhoneNumber() != null) user.setPhoneNumber(request.getPhoneNumber());

        if (request.getDateOfBirth() != null) user.setDateOfBirth(request.getDateOfBirth());
    }
}
