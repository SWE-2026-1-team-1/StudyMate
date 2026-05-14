package com.studymate.auth.exception;

import com.studymate.common.exception.BusinessException;
import com.studymate.common.exception.ErrorCode;

public class AuthException extends BusinessException {

    public AuthException(ErrorCode errorCode) {
        super(errorCode);
    }

    public AuthException(ErrorCode errorCode, String overrideMessage) {
        super(errorCode, overrideMessage);
    }
}
