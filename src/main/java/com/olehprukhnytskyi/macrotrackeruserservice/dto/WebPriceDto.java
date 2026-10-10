package com.olehprukhnytskyi.macrotrackeruserservice.dto;

public record WebPriceDto(String plan, long unitAmount, String currency, int fractionDigits) {
}
