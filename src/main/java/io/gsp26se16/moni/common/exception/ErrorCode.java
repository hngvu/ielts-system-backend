package io.gsp26se16.moni.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
public enum ErrorCode {
    // --- 1. SYSTEM & GENERAL ---
    UNCATEGORIZED_EXCEPTION(9999, "Uncategorized error", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_KEY(1001, "Uncategorized error", HttpStatus.BAD_REQUEST),

    // --- 2. AUTHENTICATION & USER  ---
    USER_EXISTED(1002, "User existed", HttpStatus.BAD_REQUEST),
    USERNAME_INVALID(1003, "Username must be at least {min} characters", HttpStatus.BAD_REQUEST),
    INVALID_PASSWORD(1004, "Password must be at least {min} characters", HttpStatus.BAD_REQUEST),
    USER_NOT_EXISTED(1005, "User not existed", HttpStatus.NOT_FOUND),
    UNAUTHENTICATED(1006, "Unauthenticated", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(1007, "You do not have permission", HttpStatus.FORBIDDEN),
    EMAIL_EXISTED(1008, "Email has been used", HttpStatus.BAD_REQUEST),
    PASSWORD_EXISTED(
            1009, "Password existed", HttpStatus.BAD_REQUEST), // Case này ít dùng, thường gộp vào INVALID_PASSWORD
    UNVERIFIED_EMAIL(1010, "Unverified email", HttpStatus.UNAUTHORIZED),
    OTP_EXPIRED(1011, "OTP expired", HttpStatus.UNAUTHORIZED),
    PROFILE_CREATION_FAILED(1012, "Profile creation failed", HttpStatus.INTERNAL_SERVER_ERROR),
    USER_NOT_ACTIVE(1013, "User is not active", HttpStatus.UNAUTHORIZED),
    USER_ALREADY_ACTIVE(1014, "User is already active", HttpStatus.BAD_REQUEST),
    USER_ALREADY_DEACTIVATED(1015, "User is already deactivated", HttpStatus.BAD_REQUEST),
    PASSWORD_INCORRECT(1016, "Password incorrect", HttpStatus.BAD_REQUEST),

    // --- VALIDATION: EMAIL & PASSWORD ---
    EMAIL_REQUIRED(1016, "Email is required", HttpStatus.BAD_REQUEST),
    INVALID_EMAIL_FORMAT(1017, "Invalid email format", HttpStatus.BAD_REQUEST),
    PASSWORD_REQUIRED(1018, "Password is required", HttpStatus.BAD_REQUEST),
    PASSWORD_TOO_SHORT(1019, "Password must be at least 8 characters", HttpStatus.BAD_REQUEST),
    PASSWORD_WEAK(1020, "Password must contain uppercase, lowercase and number", HttpStatus.BAD_REQUEST),

    FULL_NAME_REQUIRED(1021, "Full name is required", HttpStatus.BAD_REQUEST),
    FULL_NAME_LENGTH_INVALID(1022, "Full name must be between 2 and 100 characters", HttpStatus.BAD_REQUEST),
    INVALID_AVATAR_URL(1023, "Avatar URL is invalid", HttpStatus.BAD_REQUEST),

    INVALID_PHONE_NUMBER(1024, "Phone number format is invalid", HttpStatus.BAD_REQUEST),
    DOB_REQUIRED(1025, "Date of birth is required", HttpStatus.BAD_REQUEST),
    DOB_MUST_BE_IN_PAST(1026, "Date of birth must be in the past", HttpStatus.BAD_REQUEST),

    // --- VALIDATION: IELTS SPECS ---
    TARGET_BAND_REQUIRED(1027, "Target band is required", HttpStatus.BAD_REQUEST),
    INVALID_IELTS_BAND(1028, "IELTS band must be between 0.0 and 9.0", HttpStatus.BAD_REQUEST),
    BAND_MUST_BE_HALVES(1029, "IELTS band must be in 0.5 increments (e.g. 6.0, 6.5)", HttpStatus.BAD_REQUEST),

    // --- ROADMAP & STUDY ---
    ROADMAP_NOT_FOUND(1040, "Study roadmap not found", HttpStatus.NOT_FOUND),
    TEST_NOT_FOUND(1041, "IELTS test not found", HttpStatus.NOT_FOUND),
    VOCAB_COLLECTION_NOT_FOUND(1042, "Vocabulary collection not found", HttpStatus.NOT_FOUND),
    ;

    ErrorCode(int code, String message, HttpStatusCode statusCode) {
        this.code = code;
        this.message = message;
        this.statusCode = statusCode;
    }

    private final int code;
    private final String message;
    private final HttpStatusCode statusCode;
}
