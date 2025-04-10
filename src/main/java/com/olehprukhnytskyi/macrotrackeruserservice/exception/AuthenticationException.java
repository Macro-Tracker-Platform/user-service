package com.olehprukhnytskyi.macrotrackeruserservice.exception;

public class AuthenticationException extends RuntimeException {
    public AuthenticationException(String message) {
        super(message);
    }
}
