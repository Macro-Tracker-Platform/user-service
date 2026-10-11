package com.olehprukhnytskyi.macrotrackeruserservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GoogleWebCodeRequestDto(
        @NotBlank @Size(max = 4096) String code,
        @NotBlank @Size(max = 128) String state) {
}
