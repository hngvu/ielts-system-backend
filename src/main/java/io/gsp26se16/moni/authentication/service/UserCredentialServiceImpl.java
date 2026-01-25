package io.gsp26se16.moni.authentication.service;

import io.gsp26se16.moni.authentication.dto.request.ChangePassWordRequest;
import io.gsp26se16.moni.authentication.dto.request.RegisterRequest;
import io.gsp26se16.moni.authentication.dto.response.UserProfileResponse;
import io.gsp26se16.moni.authentication.entity.UserCredentials;
import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.authentication.mapper.UserCredentialsMapper;
import io.gsp26se16.moni.authentication.mapper.UserMapper;
import io.gsp26se16.moni.authentication.repository.UserCredentialsRepository;
import io.gsp26se16.moni.authentication.repository.UsersRepository;
import io.gsp26se16.moni.common.exception.AppException;
import io.gsp26se16.moni.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserCredentialServiceImpl implements UserCredentialService {
    private final UserCredentialsRepository userCredentialsRepository;
    private final UsersRepository usersRepository;
    private final UserMapper userMapper;
    private final UserCredentialsMapper userCredentialsMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserProfileResponse register(RegisterRequest request) {
        if (userCredentialsRepository.existsByEmail(request.getEmail()))
            throw new AppException(ErrorCode.EMAIL_EXISTED);

        // Create and save user entity first
        Users user = userMapper.toUser(request);
        Users savedUser = usersRepository.save(user);

        // Create credentials entity
        UserCredentials userCredentials = userCredentialsMapper.toUserCredentials(request);
        userCredentials.setPassword(passwordEncoder.encode(request.getPassword()));
        userCredentials.setUser(savedUser);

        // Save credentials
        userCredentialsRepository.save(userCredentials);

        // Reload user to get credential relationship
        Users reloadedUser = usersRepository
                .findById(savedUser.getId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        return userMapper.toUserProfileResponse(reloadedUser);
    }

    @Override
    public void banUser(String userId) {
        UserCredentials userCred = userCredentialsRepository
                .findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
        userCred.setActive(false);
        userCredentialsRepository.save(userCred);
    }

    @Override
    public void changePassword(String id, ChangePassWordRequest request) {

        UserCredentials userCred =
                userCredentialsRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
        if (!passwordEncoder.matches(request.getOldPassword(), userCred.getPassword())) {
            throw new AppException(ErrorCode.PASSWORD_INCORRECT);
        }

        if (passwordEncoder.matches(request.getNewPassword(), userCred.getPassword())) {
            throw new AppException(ErrorCode.PASSWORD_EXISTED);
        }
        userCred.setTokenVersion(userCred.getTokenVersion() + 1);

        userCred.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userCredentialsRepository.save(userCred);
    }
}
