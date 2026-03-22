package io.gsp26se16.moni.authentication.config;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.gsp26se16.moni.common.dto.ApiResponse;
import io.gsp26se16.moni.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {
    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {

        if (request.getRequestURI().contains("/payments/sepay")) {
            log.warn("SePay webhook encountered an auth exception but skipping EntryPoint: {}", authException.getMessage());
            return; // Thoát ra để không ghi đè response 401 vào body
        }

        log.error("Unauthorized error at path: {} - {}", request.getRequestURI(), authException.getMessage());
        ErrorCode errorCode = ErrorCode.UNAUTHENTICATED;

        response.setStatus(errorCode.getStatusCode().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ApiResponse<?> apiResponse = ApiResponse.builder()
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .build();

        ObjectMapper objectMapper = new ObjectMapper();

        response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
        response.flushBuffer();
    }

}
