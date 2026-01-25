package io.gsp26se16.moni.authentication.controller;


import io.gsp26se16.moni.authentication.dto.request.UpdateProfileRequest;
import io.gsp26se16.moni.authentication.dto.response.UserProfileResponse;
import io.gsp26se16.moni.authentication.service.UserService;
import io.gsp26se16.moni.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
@Slf4j
public class UserController {
    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMyProfile() {
        UserProfileResponse result = userService.getMe();

        return ResponseEntity.ok(
                ApiResponse.<UserProfileResponse>builder().result(result).build());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getUserById(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.<UserProfileResponse>builder()
                .result(userService.getUserProfile(id))
                .build());
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<UserProfileResponse>>> getAllUsers() {
        return ResponseEntity.ok(ApiResponse.<List<UserProfileResponse>>builder()
                .result(userService.getAll())
                .build());
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(
            @RequestBody @Valid UpdateProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.<UserProfileResponse>builder()
                .result(userService.updateProfile(request))
                .build());
    }
}
