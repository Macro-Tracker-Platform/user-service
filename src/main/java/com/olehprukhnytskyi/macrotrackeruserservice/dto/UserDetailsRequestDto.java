package com.olehprukhnytskyi.macrotrackeruserservice.dto;

import com.olehprukhnytskyi.util.ActivityLevel;
import com.olehprukhnytskyi.util.BodyType;
import com.olehprukhnytskyi.util.Gender;
import com.olehprukhnytskyi.util.Goal;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "User profile details for goal calculation")
public class UserDetailsRequestDto {
    @Schema(
            description = "User age in years",
            example = "28",
            requiredMode = Schema.RequiredMode.REQUIRED,
            minimum = "1"
    )
    @NotNull
    @Positive
    private Integer age;

    @Schema(
            description = "User weight in kilograms",
            example = "75",
            requiredMode = Schema.RequiredMode.REQUIRED,
            minimum = "1"
    )
    @NotNull
    @Positive
    private Integer weight;

    @Schema(
            description = "User goal weight in kilograms",
            example = "70",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED,
            minimum = "1"
    )
    @Positive
    private Integer goalWeight;

    @Schema(
            description = "Desired weekly weight change in kilograms. Negative values lose "
                    + "weight, positive values gain weight, and zero maintains weight.",
            example = "-0.4",
            minimum = "-1.0",
            maximum = "1.0"
    )
    @DecimalMin("-1.0")
    @DecimalMax("1.0")
    private BigDecimal weeklyWeightChangeKg;

    @Schema(
            description = "User height in centimeters",
            example = "180",
            requiredMode = Schema.RequiredMode.REQUIRED,
            minimum = "1"
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
