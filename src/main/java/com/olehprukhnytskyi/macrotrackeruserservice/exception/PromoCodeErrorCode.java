package com.olehprukhnytskyi.macrotrackeruserservice.exception;

import com.olehprukhnytskyi.exception.error.BaseErrorCode;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;

@AllArgsConstructor
public enum PromoCodeErrorCode implements BaseErrorCode {
    PROMO_CODE_INVALID("Promo code unavailable", HttpStatus.NOT_FOUND.value());

    private final String title;
    private final int status;

    @Override
    public String getCode() {
        return name();
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public int getStatus() {
        return status;
    }
}
