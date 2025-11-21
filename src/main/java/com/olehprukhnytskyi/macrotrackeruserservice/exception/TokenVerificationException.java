package com.olehprukhnytskyi.macrotrackeruserservice.exception;

import com.olehprukhnytskyi.exception.BaseException;
import com.olehprukhnytskyi.exception.error.BaseErrorCode;

public class TokenVerificationException extends BaseException {
    public TokenVerificationException(BaseErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public TokenVerificationException(BaseErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
