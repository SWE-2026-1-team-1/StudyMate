package com.studymate.common.exception;

import com.studymate.common.dto.ApiErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleBusiness(BusinessException e) {
        ErrorCode code = e.getErrorCode();
        return ResponseEntity
                .status(code.httpStatus())
                .body(new ApiErrorResponse(code.name(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        // - 첫 위반 메시지 사용
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getDefaultMessage())
                .orElse(ErrorCode.INVALID_INPUT.defaultMessage());
        return ResponseEntity
                .status(ErrorCode.INVALID_INPUT.httpStatus())
                .body(new ApiErrorResponse(ErrorCode.INVALID_INPUT.name(), message));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthentication(AuthenticationException e) {
        ErrorCode code = ErrorCode.UNAUTHORIZED;
        return ResponseEntity
                .status(code.httpStatus())
                .body(new ApiErrorResponse(code.name(), code.defaultMessage()));
    }
}
