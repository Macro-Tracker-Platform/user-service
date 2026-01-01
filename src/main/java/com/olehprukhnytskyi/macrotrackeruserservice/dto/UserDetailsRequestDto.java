package com.olehprukhnytskyi.macrotrackeruserservice.dto;

import com.olehprukhnytskyi.util.ActivityLevel;
import com.olehprukhnytskyi.util.BodyType;
import com.olehprukhnytskyi.util.Gender;
import com.olehprukhnytskyi.util.Goal;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "User profile details for registration")
public class UserDetailsRequestDto {
    @Schema(
            description = "User age in years",
            example = "25",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotNull
    @Positive
    private Integer age;

    @Schema(
            description = "User weight in kilograms",
            example = "70",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotNull
    @Positive
    private Integer weight;

    @Schema(
            description = "User height in centimeters",
            example = "175",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotNull
    @Positive
    private Integer height;

    @Schema(description = "User gender", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    private Gender gender;

    @Schema(description = "User activity level", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    private ActivityLevel activityLevel;

    @Schema(description = "User fitness goal", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    private Goal goal;

    @Schema(description = "User body type", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    private BodyType bodyType;
}
