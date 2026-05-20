package com.studymate.profile.exception;

import com.studymate.common.exception.BusinessException;
import com.studymate.common.exception.ErrorCode;

public class ProfileException extends BusinessException {

    public ProfileException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ProfileException(ErrorCode errorCode, String overrideMessage) {
        super(errorCode, overrideMessage);
    }
}
