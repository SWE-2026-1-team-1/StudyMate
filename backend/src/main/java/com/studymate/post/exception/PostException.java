package com.studymate.post.exception;

import com.studymate.common.exception.BusinessException;
import com.studymate.common.exception.ErrorCode;

public class PostException extends BusinessException {

    public PostException(ErrorCode errorCode) {
        super(errorCode);
    }

    public PostException(ErrorCode errorCode, String overrideMessage) {
        super(errorCode, overrideMessage);
    }
}
