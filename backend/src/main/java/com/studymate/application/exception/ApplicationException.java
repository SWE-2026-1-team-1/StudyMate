package com.studymate.application.exception;

import com.studymate.common.exception.BusinessException;
import com.studymate.common.exception.ErrorCode;

public class ApplicationException extends BusinessException {

    public ApplicationException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ApplicationException(ErrorCode errorCode, String overrideMessage) {
        super(errorCode, overrideMessage);
    }
}
