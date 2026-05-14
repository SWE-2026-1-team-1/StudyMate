package com.studymate.common.exception;

public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.defaultMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String overrideMessage) {
        super(overrideMessage);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() { return errorCode; }
}
