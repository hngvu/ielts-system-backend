package io.gsp26se16.moni.authentication.service;

import java.text.ParseException;

import com.nimbusds.jose.JOSEException;

import io.gsp26se16.moni.authentication.dto.request.AuthenticationRequest;
import io.gsp26se16.moni.authentication.dto.request.LogoutRequest;
import io.gsp26se16.moni.authentication.dto.request.RefreshRequest;
import io.gsp26se16.moni.authentication.dto.response.AuthenticationResponse;

public interface AuthenticationService {
    AuthenticationResponse outboundAuthenticate(String code);

    AuthenticationResponse authenticate(AuthenticationRequest request);

    void logout(LogoutRequest request) throws ParseException, JOSEException;

    AuthenticationResponse refreshToken(RefreshRequest request) throws ParseException, JOSEException;
}
