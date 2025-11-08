package com.olehprukhnytskyi.macrotrackeruserservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "API error details")
public class ApiError {
    @Schema(description = "Field name that caused the error", example = "email")
    private String field;

    @Schema(description = "Error message", example = "Email must be valid")
    private String message;
}
