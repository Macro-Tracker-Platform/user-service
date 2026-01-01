package com.olehprukhnytskyi.macrotrackeruserservice.dto;

import com.olehprukhnytskyi.util.ActivityLevel;
import com.olehprukhnytskyi.util.BodyType;
import com.olehprukhnytskyi.util.Gender;
import com.olehprukhnytskyi.util.Goal;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Update user profile details request")
public class UpdateUserDetailsRequestDto {
    @Schema(description = "User age in years", example = "25", minimum = "1")
    @Positive
    private Integer age;

    @Schema(description = "User weight in kilograms", example = "70", minimum = "1")
    @Positive
    private Integer weight;

    @Schema(description = "User height in centimeters", example = "175", minimum = "1")
    @Positive
    private Integer height;

    @Schema(description = "User gender")
    private Gender gender;

    @Schema(description = "User activity level")
    private ActivityLevel activityLevel;

    @Schema(description = "User fitness goal")
    private Goal goal;

    @Schema(description = "User body type")
    private BodyType bodyType;

    @Schema(description = "Recalculate nutrition goals based on new details", example = "true")
    private boolean recalculate;
}
