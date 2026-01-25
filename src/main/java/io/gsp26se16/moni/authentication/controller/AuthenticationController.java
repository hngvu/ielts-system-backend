package io.gsp26se16.moni.authentication.controller;

import com.nimbusds.jose.JOSEException;

import io.gsp26se16.moni.authentication.dto.request.AuthenticationRequest;
import io.gsp26se16.moni.authentication.dto.request.LogoutRequest;
import io.gsp26se16.moni.authentication.dto.request.RefreshRequest;
import io.gsp26se16.moni.authentication.dto.response.AuthenticationResponse;
import io.gsp26se16.moni.authentication.service.AuthenticationService;
import io.gsp26se16.moni.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.text.ParseException;

@RequiredArgsConstructor
@RestController
@RequestMapping("/auth")
@Slf4j
public class AuthenticationController {
    private final AuthenticationService authenticationService;

    @PostMapping("/token")
    public ResponseEntity<ApiResponse<AuthenticationResponse>> authenticate(
            @RequestBody AuthenticationRequest request) {
        var result = authenticationService.authenticate(request);

        return ResponseEntity.ok(
                ApiResponse.<AuthenticationResponse>builder().result(result).build());
    }

    @PostMapping("/outbound/authentication")
    public ResponseEntity<ApiResponse<AuthenticationResponse>> outboundAuthenticate(@RequestParam("code") String code) {
        var result = authenticationService.outboundAuthenticate(code);

        return ResponseEntity.ok(
                ApiResponse.<AuthenticationResponse>builder().result(result).build());
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthenticationResponse>> refresh(@RequestBody RefreshRequest request)
            throws ParseException, JOSEException {
        var result = authenticationService.refreshToken(request);

        return ResponseEntity.ok(
                ApiResponse.<AuthenticationResponse>builder().result(result).build());
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestBody LogoutRequest request)
            throws ParseException, JOSEException {
        authenticationService.logout(request);

        return ResponseEntity.ok(
                ApiResponse.<Void>builder().message("Logout successfully").build());
    }
}
