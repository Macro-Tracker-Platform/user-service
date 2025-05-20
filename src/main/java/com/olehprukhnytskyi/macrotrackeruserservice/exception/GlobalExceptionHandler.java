package com.olehprukhnytskyi.macrotrackeruserservice.exception;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.ApiError;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.ApiResponse;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<?>> handleIllegalArgument(IllegalArgumentException ex) {
        ApiError error = new ApiError("provider", ex.getMessage());
        return ResponseEntity.badRequest().body(ApiResponse.error(error));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<?>> handleAuthentication(AuthenticationException ex) {
        ApiError error = new ApiError("token", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(error));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<?>> handleValidation(
            MethodArgumentNotValidException ex) {
        String field = ex.getBindingResult().getFieldErrors().get(0).getField();
        String message = ex.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        ApiError error = new ApiError(field, message);
        return ResponseEntity.badRequest().body(ApiResponse.error(error));
    }

    @ExceptionHandler(TokenVerificationException.class)
    public ResponseEntity<ApiResponse<?>> handleTokenVerificationException(
            TokenVerificationException ex) {
        ApiError error = new ApiError("token", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(error));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleGenericException(Exception ex) {
        ApiError error = new ApiError("server", "An unexpected error occurred");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(error));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(NotFoundException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }
}
