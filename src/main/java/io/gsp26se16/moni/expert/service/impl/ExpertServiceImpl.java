package io.gsp26se16.moni.expert.service.impl;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.gsp26se16.moni.authentication.entity.UserCredentials;
import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.authentication.repository.UserCredentialsRepository;
import io.gsp26se16.moni.authentication.repository.UsersRepository;
import io.gsp26se16.moni.common.exception.AppException;
import io.gsp26se16.moni.common.exception.ErrorCode;
import io.gsp26se16.moni.expert.dto.CreateExpertRequest;
import io.gsp26se16.moni.expert.dto.ExpertProfileResponse;
import io.gsp26se16.moni.expert.entity.ExpertProfile;
import io.gsp26se16.moni.expert.enumeration.ExpertSpecialization;
import io.gsp26se16.moni.expert.enumeration.ExpertStatus;
import io.gsp26se16.moni.expert.repository.ExpertProfileRepository;
import io.gsp26se16.moni.expert.service.ExpertService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ExpertServiceImpl implements ExpertService {

    ExpertProfileRepository expertProfileRepository;
    UserCredentialsRepository userCredentialsRepository;
    UsersRepository usersRepository;
    PasswordEncoder passwordEncoder;

    @Override
    public List<ExpertProfileResponse> listExperts(ExpertSpecialization filter) {
        List<ExpertProfile> experts;
        if (filter != null) {
            experts = expertProfileRepository.findBySpecializationAndStatus(filter, ExpertStatus.AVAILABLE);
        } else {
            experts = expertProfileRepository.findAll();
        }
        return experts.stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public ExpertProfileResponse getExpert(Integer id) {
        ExpertProfile profile =
                expertProfileRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.EXPERT_NOT_FOUND));
        return toResponse(profile);
    }

    @Override
    @Transactional
    public ExpertProfileResponse createExpert(CreateExpertRequest request) {
        if (userCredentialsRepository.existsByEmail(request.getEmail())) {
            throw new AppException(ErrorCode.EMAIL_EXISTED);
        }

        Users user = Users.builder()
                .full_name(request.getDisplayName())
                .avatar_url(request.getAvatarUrl())
                .credit(0.0)
                .build();
        Users savedUser = usersRepository.save(user);

        UserCredentials credential = UserCredentials.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(UserCredentials.Role.EXPERT)
                .user(savedUser)
                .build();
        userCredentialsRepository.save(credential);

        ExpertProfile profile = ExpertProfile.builder()
                .user(savedUser)
                .displayName(request.getDisplayName())
                .avatarUrl(request.getAvatarUrl())
                .bandScore(request.getBandScore())
                .yearsExperience(request.getYearsExperience())
                .specialization(request.getSpecialization())
                .bio(request.getBio())
                .status(ExpertStatus.OFFLINE)
                .rating(0.0)
                .totalSessions(0)
                .build();

        return toResponse(expertProfileRepository.save(profile));
    }

    @Override
    @Transactional
    public void updateStatus(Integer id, ExpertStatus status) {
        ExpertProfile profile =
                expertProfileRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.EXPERT_NOT_FOUND));
        profile.setStatus(status);
        expertProfileRepository.save(profile);
    }

    @Override
    @Transactional
    public void deleteExpert(Integer id) {
        ExpertProfile profile =
                expertProfileRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.EXPERT_NOT_FOUND));
        expertProfileRepository.delete(profile);
    }

    private ExpertProfileResponse toResponse(ExpertProfile p) {
        return ExpertProfileResponse.builder()
                .id(p.getId())
                .displayName(p.getDisplayName())
                .avatarUrl(p.getAvatarUrl())
                .bandScore(p.getBandScore())
                .yearsExperience(p.getYearsExperience())
                .specialization(p.getSpecialization())
                .bio(p.getBio())
                .status(p.getStatus())
                .rating(p.getRating())
                .totalSessions(p.getTotalSessions())
                .build();
    }
}
