package com.olehprukhnytskyi.macrotrackeruserservice.exception;

import com.olehprukhnytskyi.exception.BaseException;
import com.olehprukhnytskyi.exception.error.BaseErrorCode;

public class AuthenticationException extends BaseException {
    public AuthenticationException(BaseErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
