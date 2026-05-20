package com.studymate.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    // 400 Bad Request
    INVALID_INPUT         (HttpStatus.BAD_REQUEST,   "입력값 형식이 올바르지 않습니다."),
    INVALID_EMAIL_CODE    (HttpStatus.BAD_REQUEST,   "인증 코드가 올바르지 않습니다."),
    EXPIRED_EMAIL_CODE    (HttpStatus.BAD_REQUEST,   "인증 코드가 만료되었습니다."),
    EMAIL_NOT_VERIFIED    (HttpStatus.BAD_REQUEST,   "이메일 인증이 완료되지 않았습니다."),

    // 401 Unauthorized
    UNAUTHORIZED          (HttpStatus.UNAUTHORIZED,  "인증되지 않은 사용자입니다."),
    INVALID_CREDENTIALS   (HttpStatus.UNAUTHORIZED,  "이메일 또는 비밀번호가 올바르지 않습니다."),
    TOKEN_EXPIRED         (HttpStatus.UNAUTHORIZED,  "리프레시 토큰이 만료되었습니다."),
    INVALID_TOKEN         (HttpStatus.UNAUTHORIZED,  "유효하지 않은 토큰입니다."),

    // 403 Forbidden
    FORBIDDEN             (HttpStatus.FORBIDDEN,     "권한이 없습니다."),

    // 404 Not Found
    NOT_FOUND             (HttpStatus.NOT_FOUND,     "리소스를 찾을 수 없습니다."),

    // 409 Conflict
    CONFLICT              (HttpStatus.CONFLICT,      "이미 가입된 이메일입니다."),
    INVALID_MAX_MEMBERS   (HttpStatus.CONFLICT,      "현재 멤버 수보다 작게 줄일 수 없습니다. 먼저 팀원을 강퇴해 주세요."),
    INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT,   "현재 상태에서 요청한 상태로 전환할 수 없습니다."),
    INVALID_STATUS_FOR_UPDATE(HttpStatus.CONFLICT,   "마감된 스터디에서는 변경할 수 없는 항목입니다.");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus httpStatus() { return httpStatus; }
    public String defaultMessage() { return defaultMessage; }
}
