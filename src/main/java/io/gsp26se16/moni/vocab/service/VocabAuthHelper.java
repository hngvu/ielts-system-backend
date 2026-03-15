package io.gsp26se16.moni.vocab.service;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.authentication.repository.UserCredentialsRepository;
import io.gsp26se16.moni.common.exception.AppException;
import io.gsp26se16.moni.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;

/**
 * Shared helper to resolve current user from SecurityContext.
 * JWT "userId" claim = UserCredentials.id → users.getUser() returns Users.
 */
@Component
@RequiredArgsConstructor
public class VocabAuthHelper {

    private final UserCredentialsRepository userCredentialsRepository;

    /** Extract Users entity from current JWT. */
    public Users getCurrentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        String credentialId = null;
        if (auth.getPrincipal() instanceof Jwt jwt) {
            credentialId = jwt.getClaimAsString("userId");
        }
        if (credentialId == null) throw new AppException(ErrorCode.UNAUTHENTICATED);
        return getUser(credentialId);
    }

    /** Resolve Users from a credential ID. */
    public Users getUser(String credentialId) {
        var cred = userCredentialsRepository
                .findById(credentialId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
        if (cred.getUser() == null) throw new AppException(ErrorCode.USER_NOT_EXISTED);
        return cred.getUser();
    }
}
