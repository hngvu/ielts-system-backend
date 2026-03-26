package io.gsp26se16.moni.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import lombok.Getter;

@Getter
public enum ErrorCode {
    // --- 1. SYSTEM & GENERAL ---
    UNCATEGORIZED_EXCEPTION(9999, "Đã xảy ra lỗi không xác định", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_KEY(1001, "Yêu cầu không hợp lệ", HttpStatus.BAD_REQUEST),

    // --- 2. AUTHENTICATION & USER  ---
    USER_EXISTED(1002, "Tài khoản đã tồn tại", HttpStatus.BAD_REQUEST),
    USERNAME_INVALID(1003, "Tên đăng nhập phải có ít nhất {min} ký tự", HttpStatus.BAD_REQUEST),
    INVALID_PASSWORD(1004, "Mật khẩu phải có ít nhất {min} ký tự", HttpStatus.BAD_REQUEST),
    USER_NOT_EXISTED(1005, "Không tìm thấy người dùng", HttpStatus.NOT_FOUND),
    UNAUTHENTICATED(1006, "Chưa đăng nhập hoặc phiên đã hết hạn", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(1007, "Bạn không có quyền thực hiện thao tác này", HttpStatus.FORBIDDEN),
    EMAIL_EXISTED(1008, "Email này đã được sử dụng", HttpStatus.BAD_REQUEST),
    PASSWORD_EXISTED(1009, "Mật khẩu đã tồn tại", HttpStatus.BAD_REQUEST),
    UNVERIFIED_EMAIL(1010, "Email chưa được xác thực", HttpStatus.UNAUTHORIZED),
    OTP_EXPIRED(1011, "Mã OTP đã hết hạn", HttpStatus.UNAUTHORIZED),
    PROFILE_CREATION_FAILED(1012, "Tạo hồ sơ thất bại", HttpStatus.INTERNAL_SERVER_ERROR),
    USER_NOT_ACTIVE(1013, "Tài khoản chưa được kích hoạt", HttpStatus.UNAUTHORIZED),
    USER_ALREADY_ACTIVE(1014, "Tài khoản đã được kích hoạt rồi", HttpStatus.BAD_REQUEST),
    USER_ALREADY_DEACTIVATED(1015, "Tài khoản đã bị vô hiệu hóa rồi", HttpStatus.BAD_REQUEST),
    PASSWORD_INCORRECT(1016, "Mật khẩu không đúng", HttpStatus.BAD_REQUEST),

    // --- VALIDATION: EMAIL & PASSWORD ---
    EMAIL_REQUIRED(1016, "Vui lòng nhập email", HttpStatus.BAD_REQUEST),
    INVALID_EMAIL_FORMAT(1017, "Định dạng email không đúng", HttpStatus.BAD_REQUEST),
    PASSWORD_REQUIRED(1018, "Vui lòng nhập mật khẩu", HttpStatus.BAD_REQUEST),
    PASSWORD_TOO_SHORT(1019, "Mật khẩu phải có ít nhất 8 ký tự", HttpStatus.BAD_REQUEST),
    PASSWORD_WEAK(1020, "Mật khẩu phải bao gồm chữ hoa, chữ thường và số", HttpStatus.BAD_REQUEST),

    FULL_NAME_REQUIRED(1021, "Vui lòng nhập họ tên", HttpStatus.BAD_REQUEST),
    FULL_NAME_LENGTH_INVALID(1022, "Họ tên phải từ 2 đến 100 ký tự", HttpStatus.BAD_REQUEST),
    INVALID_AVATAR_URL(1023, "URL avatar không hợp lệ", HttpStatus.BAD_REQUEST),

    INVALID_PHONE_NUMBER(1024, "Số điện thoại không hợp lệ", HttpStatus.BAD_REQUEST),
    DOB_REQUIRED(1025, "Vui lòng nhập ngày sinh", HttpStatus.BAD_REQUEST),
    DOB_MUST_BE_IN_PAST(1026, "Ngày sinh phải trong quá khứ", HttpStatus.BAD_REQUEST),

    // --- VALIDATION: IELTS SPECS ---
    TARGET_BAND_REQUIRED(1027, "Vui lòng nhập band mục tiêu", HttpStatus.BAD_REQUEST),
    INVALID_IELTS_BAND(1028, "Band IELTS phải từ 0.0 đến 9.0", HttpStatus.BAD_REQUEST),
    BAND_MUST_BE_HALVES(1029, "Band IELTS phải theo bước 0.5 (VD: 6.0, 6.5)", HttpStatus.BAD_REQUEST),

    // --- ROADMAP & STUDY ---
    ROADMAP_NOT_FOUND(1040, "Không tìm thấy lộ trình học", HttpStatus.NOT_FOUND),
    TEST_NOT_FOUND(1041, "Không tìm thấy bài thi IELTS", HttpStatus.NOT_FOUND),
    VOCAB_COLLECTION_NOT_FOUND(1042, "Không tìm thấy bộ từ vựng", HttpStatus.NOT_FOUND),
    VOCAB_NOT_FOUND(1049, "Không tìm thấy từ vựng", HttpStatus.NOT_FOUND),
    GOAL_NOT_FOUND(1058, "Không tìm thấy mục tiêu học tập", HttpStatus.NOT_FOUND),
    TASK_NOT_FOUND(1059, "Không tìm thấy bài tập", HttpStatus.NOT_FOUND),
    ACTIVE_ROADMAP_NOT_FOUND(1060, "Không tìm thấy lộ trình đang chạy", HttpStatus.NOT_FOUND),

    // --- TAG MODULE ---
    TAG_EXISTED(1043, "Mã hoặc tên tag đã tồn tại", HttpStatus.BAD_REQUEST),
    TAG_NOT_FOUND(1044, "Không tìm thấy tag", HttpStatus.NOT_FOUND),

    // --- PRACTICE MODULE ---
    STIMULUS_NOT_FOUND(1045, "Không tìm thấy nội dung bài thi", HttpStatus.NOT_FOUND),
    QUESTION_NOT_FOUND(1046, "Không tìm thấy câu hỏi", HttpStatus.NOT_FOUND),
    OPTION_NOT_FOUND(1047, "Không tìm thấy đáp án", HttpStatus.NOT_FOUND),
    ATTEMPT_NOT_FOUND(1048, "Không tìm thấy lần làm bài", HttpStatus.NOT_FOUND),
    QUESTION_GROUP_NOT_FOUND(1061, "Không tìm thấy nhóm câu hỏi", HttpStatus.NOT_FOUND),

    // --- PLACEMENT MODULE ---
    PLACEMENT_NO_READING_TEST(1050, "Không có bài thi Reading nào được công bố", HttpStatus.NOT_FOUND),
    PLACEMENT_NO_LISTENING_TEST(1051, "Không có bài thi Listening nào được công bố", HttpStatus.NOT_FOUND),
    PLACEMENT_INVALID_BAND(1052, "Điểm band không hợp lệ (0-9, bước 0.5)", HttpStatus.BAD_REQUEST),
    PLACEMENT_NOT_FOUND(1053, "Không tìm thấy kết quả kiểm tra trình độ", HttpStatus.NOT_FOUND),

    // --- CREDIT & PAYMENT ---
    INSUFFICIENT_CREDIT(1054, "Số dư credit không đủ", HttpStatus.BAD_REQUEST),
    PACKAGE_PRICING_NOT_FOUND(1062, "Không tìm thấy gói nạp", HttpStatus.NOT_FOUND),
    SERVICE_PRICING_NOT_FOUND(1063, "Không tìm thấy bảng giá dịch vụ", HttpStatus.NOT_FOUND),
    CREDIT_TRANSACTION_NOT_FOUND(1064, "Không tìm thấy giao dịch credit", HttpStatus.NOT_FOUND),

    // --- AI MODULE ---
    AI_EVALUATION_FAILED(1070, "Đánh giá AI thất bại", HttpStatus.INTERNAL_SERVER_ERROR),
    AI_SERVICE_UNAVAILABLE(1071, "Dịch vụ AI tạm thời không khả dụng", HttpStatus.SERVICE_UNAVAILABLE),

    // --- EXPERT MODULE ---
    EXPERT_NOT_FOUND(1055, "Không tìm thấy chuyên gia", HttpStatus.NOT_FOUND),
    SCORING_SESSION_NOT_FOUND(1056, "Không tìm thấy phiên chấm điểm", HttpStatus.NOT_FOUND),
    SESSION_NOT_CANCELLABLE(1057, "Phiên này không thể huỷ", HttpStatus.BAD_REQUEST),

    // --- EXAM SESSION MODULE ---
    EXAM_SESSION_ALREADY_EXISTS(1080, "Đã có phiên thi đang diễn ra cho bài thi này", HttpStatus.CONFLICT),
    EXAM_SESSION_NOT_FOUND(1081, "Không tìm thấy phiên thi", HttpStatus.NOT_FOUND),
    EXAM_SESSION_EXPIRED(1082, "Phiên thi đã hết hạn", HttpStatus.GONE),
    EXAM_SESSION_ALREADY_SUBMITTED(1083, "Phiên thi đã được nộp", HttpStatus.CONFLICT),
    TEST_NO_DURATION(1084, "Bài thi không có thời gian giới hạn", HttpStatus.BAD_REQUEST),
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
