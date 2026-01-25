package io.gsp26se16.moni.authentication.controller;

import io.gsp26se16.moni.authentication.dto.request.ChangePassWordRequest;
import io.gsp26se16.moni.authentication.dto.request.RegisterRequest;
import io.gsp26se16.moni.authentication.dto.response.UserProfileResponse;
import io.gsp26se16.moni.authentication.service.UserCredentialService;
import io.gsp26se16.moni.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/credentials")
@Slf4j
public class UserCredentialController {
    private final UserCredentialService userCredentialService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserProfileResponse>> register(@RequestBody @Valid RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<UserProfileResponse>builder()
                        .result(userCredentialService.register(request))
                        .message("Register successfully")
                        .build());
    }

    @PutMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(@RequestBody @Valid ChangePassWordRequest request) {
        var context = SecurityContextHolder.getContext();
        String userId = context.getAuthentication().getName();

        userCredentialService.changePassword(userId, request);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .message("Password changed successfully")
                .build());
    }

    @PutMapping("/{id}/ban")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> banUser(@PathVariable String id) {
        userCredentialService.banUser(id);

        return ResponseEntity.ok(
                ApiResponse.<Void>builder().message("User has been banned").build());
    }
}
