package com.studymate.study.exception;

import com.studymate.common.exception.BusinessException;
import com.studymate.common.exception.ErrorCode;

public class StudyException extends BusinessException {

    public StudyException(ErrorCode errorCode) {
        super(errorCode);
    }

    public StudyException(ErrorCode errorCode, String overrideMessage) {
        super(errorCode, overrideMessage);
    }
}
