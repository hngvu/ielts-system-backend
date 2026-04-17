package io.gsp26se16.moni.expert.service.impl;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.gsp26se16.moni.authentication.entity.UserCredentials;
import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.authentication.repository.UserCredentialsRepository;
import io.gsp26se16.moni.authentication.repository.UsersRepository;
import io.gsp26se16.moni.common.exception.AppException;
import io.gsp26se16.moni.common.exception.ErrorCode;
import io.gsp26se16.moni.expert.dto.CreateExpertRequest;
import io.gsp26se16.moni.expert.dto.ExpertProfileResponse;
import io.gsp26se16.moni.expert.dto.UpdateExpertRequest;
import io.gsp26se16.moni.expert.entity.ExpertProfile;
import io.gsp26se16.moni.expert.enumeration.ExpertSpecialization;
import io.gsp26se16.moni.expert.enumeration.ExpertStatus;
import io.gsp26se16.moni.expert.repository.ExpertProfileRepository;
import io.gsp26se16.moni.expert.service.ExpertService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ExpertServiceImpl implements ExpertService {

    ExpertProfileRepository expertProfileRepository;
    UserCredentialsRepository userCredentialsRepository;
    UsersRepository usersRepository;
    PasswordEncoder passwordEncoder;
    ObjectMapper objectMapper;

    @Override
    @Transactional
    public List<ExpertProfileResponse> listExperts(ExpertSpecialization filter) {
        List<ExpertProfile> experts;
        if (filter != null) {
            experts = expertProfileRepository.findBySpecializationIn(
                    java.util.List.of(filter, ExpertSpecialization.BOTH));
        } else {
            experts = expertProfileRepository.findAll();
        }

        // Auto-set OFFLINE nếu không ping trong 30s (không lọc, chỉ đồng bộ trạng thái hiển thị)
        java.time.LocalDateTime threshold = java.time.LocalDateTime.now().minusSeconds(30);
        for (ExpertProfile ep : experts) {
            if (ep.getStatus() == ExpertStatus.AVAILABLE
                    && (ep.getLastActiveAt() == null || ep.getLastActiveAt().isBefore(threshold))) {
                ep.setStatus(ExpertStatus.OFFLINE);
                expertProfileRepository.save(ep);
            }
        }

        return experts.stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ExpertProfileResponse getExpert(Integer id) {
        ExpertProfile profile =
                expertProfileRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.EXPERT_NOT_FOUND));
        // Auto-set OFFLINE nếu không ping trong 30s
        if (profile.getStatus() == ExpertStatus.AVAILABLE) {
            java.time.LocalDateTime threshold = java.time.LocalDateTime.now().minusSeconds(30);
            if (profile.getLastActiveAt() == null || profile.getLastActiveAt().isBefore(threshold)) {
                profile.setStatus(ExpertStatus.OFFLINE);
                expertProfileRepository.save(profile);
            }
        }
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

        String rawPassword =
                (request.getPassword() != null && !request.getPassword().isBlank())
                        ? request.getPassword()
                        : "12345678";

        UserCredentials credential = UserCredentials.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(rawPassword))
                .role(UserCredentials.Role.EXPERT)
                .user(savedUser)
                .build();
        userCredentialsRepository.save(credential);

        double bandScore = calcBandScore(
                request.getBandReading(),
                request.getBandListening(),
                request.getBandWriting(),
                request.getBandSpeaking());

        ExpertProfile profile = ExpertProfile.builder()
                .user(savedUser)
                .displayName(request.getDisplayName())
                .avatarUrl(request.getAvatarUrl())
                .bandScore(bandScore)
                .bandReading(request.getBandReading())
                .bandListening(request.getBandListening())
                .bandWriting(request.getBandWriting())
                .bandSpeaking(request.getBandSpeaking())
                .yearsExperience(request.getYearsExperience())
                .bio(request.getBio())
                .certificates(serializeCertificates(request.getCertificates()))
                .rating(0.0)
                .totalSessions(0)
                .build();

        return toResponse(expertProfileRepository.save(profile));
    }

    @Override
    @Transactional
    public ExpertProfileResponse updateExpert(Integer id, UpdateExpertRequest request) {
        ExpertProfile profile =
                expertProfileRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.EXPERT_NOT_FOUND));

        if (request.getDisplayName() != null) profile.setDisplayName(request.getDisplayName());
        if (request.getAvatarUrl() != null) profile.setAvatarUrl(request.getAvatarUrl());
        if (request.getBandReading() != null) profile.setBandReading(request.getBandReading());
        if (request.getBandListening() != null) profile.setBandListening(request.getBandListening());
        if (request.getBandWriting() != null) profile.setBandWriting(request.getBandWriting());
        if (request.getBandSpeaking() != null) profile.setBandSpeaking(request.getBandSpeaking());
        if (request.getYearsExperience() != null) profile.setYearsExperience(request.getYearsExperience());
        if (request.getBio() != null) profile.setBio(request.getBio());
        if (request.getCertificates() != null)
            profile.setCertificates(serializeCertificates(request.getCertificates()));

        // Recalculate bandScore from current values
        profile.setBandScore(calcBandScore(
                profile.getBandReading(),
                profile.getBandListening(),
                profile.getBandWriting(),
                profile.getBandSpeaking()));

        return toResponse(expertProfileRepository.save(profile));
    }

    @Override
    @Transactional
    public ExpertProfileResponse updateStatus(Integer id, ExpertStatus status) {
        ExpertProfile profile =
                expertProfileRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.EXPERT_NOT_FOUND));
        profile.setStatus(status);
        return toResponse(expertProfileRepository.save(profile));
    }

    @Override
    @Transactional
    public ExpertProfileResponse updateAccountStatus(Integer id, boolean enabled) {
        ExpertProfile profile =
                expertProfileRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.EXPERT_NOT_FOUND));

        var cred = userCredentialsRepository.findByUser_Id(profile.getUser().getId());
        if (cred.isPresent()) {
            cred.get().setActive(enabled);
            userCredentialsRepository.save(cred.get());
        }

        // If disabling account, also set expert status to OFFLINE
        if (!enabled) {
            profile.setStatus(ExpertStatus.OFFLINE);
            expertProfileRepository.save(profile);
        }

        return toResponse(profile);
    }

    @Override
    public ExpertProfileResponse updateMyProfile(String credentialId, UpdateExpertRequest request) {
        var cred = userCredentialsRepository
                .findById(credentialId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
        ExpertProfile profile = expertProfileRepository
                .findByUser_Id(cred.getUser().getId())
                .orElseThrow(() -> new AppException(ErrorCode.EXPERT_NOT_FOUND));

        return updateExpert(profile.getId(), request);
    }

    @Override
    public ExpertProfileResponse getMyProfile(String credentialId) {
        var cred = userCredentialsRepository
                .findById(credentialId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
        ExpertProfile profile = expertProfileRepository
                .findByUser_Id(cred.getUser().getId())
                .orElseThrow(() -> new AppException(ErrorCode.EXPERT_NOT_FOUND));
        return toResponse(profile);
    }

    @Override
    @Transactional
    public void updateMyStatus(String credentialId, ExpertStatus status) {
        var cred = userCredentialsRepository
                .findById(credentialId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
        ExpertProfile profile = expertProfileRepository
                .findByUser_Id(cred.getUser().getId())
                .orElseThrow(() -> new AppException(ErrorCode.EXPERT_NOT_FOUND));
        profile.setStatus(status);
        if (status == ExpertStatus.AVAILABLE) {
            profile.setLastActiveAt(java.time.LocalDateTime.now());
        }
        expertProfileRepository.save(profile);
    }

    @Override
    @Transactional
    public void deleteExpert(Integer id) {
        ExpertProfile profile =
                expertProfileRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.EXPERT_NOT_FOUND));
        expertProfileRepository.delete(profile);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Round average of 4 band scores to nearest 0.5. Returns 0.0 if all nulls. */
    private double calcBandScore(Double reading, Double listening, Double writing, Double speaking) {
        double r = reading != null ? reading : 0.0;
        double l = listening != null ? listening : 0.0;
        double w = writing != null ? writing : 0.0;
        double s = speaking != null ? speaking : 0.0;
        return Math.round((r + l + w + s) / 4.0 * 2) / 2.0;
    }

    private String serializeCertificates(List<String> certificates) {
        if (certificates == null || certificates.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(certificates);
        } catch (Exception e) {
            log.warn("Failed to serialize certificates", e);
            return null;
        }
    }

    private List<String> deserializeCertificates(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("Failed to deserialize certificates: {}", json);
            return Collections.emptyList();
        }
    }

    private ExpertProfileResponse toResponse(ExpertProfile p) {
        // Look up email and active status from UserCredentials
        String email = null;
        boolean enabled = true;
        if (p.getUser() != null) {
            var cred = userCredentialsRepository.findByUser_Id(p.getUser().getId());
            if (cred.isPresent()) {
                email = cred.get().getEmail();
                enabled = cred.get().isActive();
            }
        }

        return ExpertProfileResponse.builder()
                .id(p.getId())
                .displayName(p.getDisplayName())
                .email(email)
                .avatarUrl(p.getAvatarUrl())
                .bandScore(p.getBandScore())
                .bandReading(p.getBandReading())
                .bandListening(p.getBandListening())
                .bandWriting(p.getBandWriting())
                .bandSpeaking(p.getBandSpeaking())
                .yearsExperience(p.getYearsExperience())
                .specialization(p.getSpecialization())
                .bio(p.getBio())
                .status(p.getStatus())
                .enabled(enabled)
                .rating(p.getRating())
                .totalSessions(p.getTotalSessions())
                .certificates(deserializeCertificates(p.getCertificates()))
                .build();
    }
}
