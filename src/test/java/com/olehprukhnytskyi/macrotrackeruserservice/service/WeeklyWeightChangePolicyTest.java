package com.olehprukhnytskyi.macrotrackeruserservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.olehprukhnytskyi.util.Goal;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class WeeklyWeightChangePolicyTest {
    @Test
    void missingRateFromLegacyClientUsesGoalSpecificDefaults() {
        assertThat(WeeklyWeightChangePolicy.resolve(Goal.LOSE, null))
                .isEqualByComparingTo("-0.40");
        assertThat(WeeklyWeightChangePolicy.resolve(Goal.MAINTAIN, null))
                .isEqualByComparingTo("0.00");
        assertThat(WeeklyWeightChangePolicy.resolve(Goal.GAIN, null))
                .isEqualByComparingTo("0.25");
    }

    @Test
    void legacyUnsignedRateIsNormalizedForTheSelectedGoal() {
        BigDecimal legacyRate = new BigDecimal("0.40");

        assertThat(WeeklyWeightChangePolicy.resolve(Goal.LOSE, legacyRate))
                .isEqualByComparingTo("-0.40");
        assertThat(WeeklyWeightChangePolicy.resolve(Goal.MAINTAIN, legacyRate))
                .isEqualByComparingTo("0.00");
    }
}
