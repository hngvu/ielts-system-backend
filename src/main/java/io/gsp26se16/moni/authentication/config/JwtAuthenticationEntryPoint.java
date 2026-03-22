package io.gsp26se16.moni.authentication.config;

import java.io.IOException;
import java.util.Collections;

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

        // === DEBUG: Log chi tiết request bị 401 ===
        log.error("=== 401 UNAUTHORIZED DEBUG ===");
        log.error("Method: {}", request.getMethod());
        log.error("RequestURI: {}", request.getRequestURI());
        log.error("RequestURL: {}", request.getRequestURL());
        log.error("ServletPath: {}", request.getServletPath());
        log.error("ContextPath: {}", request.getContextPath());
        log.error("PathInfo: {}", request.getPathInfo());
        log.error("QueryString: {}", request.getQueryString());
        log.error("Exception: {}", authException.getMessage());

        // Log tất cả headers
        Collections.list(request.getHeaderNames())
                .forEach(headerName -> log.error("Header [{}]: {}", headerName, request.getHeader(headerName)));
        log.error("=== END 401 DEBUG ===");

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
