package io.gsp26se16.moni.authentication.controller;

import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import io.gsp26se16.moni.authentication.dto.request.ChangePassWordRequest;
import io.gsp26se16.moni.authentication.dto.request.RegisterRequest;
import io.gsp26se16.moni.authentication.dto.response.UserProfileResponse;
import io.gsp26se16.moni.authentication.service.UserCredentialService;
import io.gsp26se16.moni.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
    public ResponseEntity<ApiResponse<Map<String, String>>> changePassword(
            @RequestBody @Valid ChangePassWordRequest request) {
        var context = SecurityContextHolder.getContext();
        String userId = context.getAuthentication().getName();

        userCredentialService.changePassword(userId, request);

        return ResponseEntity.ok(ApiResponse.<Map<String, String>>builder()
                .message("Password changed successfully")
                .result(Map.of("status", "success", "message", "Password changed successfully"))
                .build());
    }

    @PutMapping("/{id}/ban")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> banUser(@PathVariable String id) {
        userCredentialService.banUser(id);

        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .message("User has been banned")
                .result(Map.of("userId", id, "active", false, "status", "banned"))
                .build());
    }
}
