package com.olehprukhnytskyi.macrotrackeruserservice.service;

import com.olehprukhnytskyi.util.Goal;
import java.math.BigDecimal;
import java.math.RoundingMode;

final class WeeklyWeightChangePolicy {
    static final BigDecimal DEFAULT_LOSS_KG = BigDecimal.valueOf(-0.4);
    static final BigDecimal DEFAULT_GAIN_KG = BigDecimal.valueOf(0.25);
    private static final BigDecimal MAX_CHANGE_KG = BigDecimal.ONE;

    private WeeklyWeightChangePolicy() {
    }

    static BigDecimal resolve(Goal goal, BigDecimal requestedChangeKg) {
        if (goal == null || goal == Goal.MAINTAIN) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);
        }
        BigDecimal fallback = goal == Goal.LOSE ? DEFAULT_LOSS_KG : DEFAULT_GAIN_KG;
        BigDecimal requested = requestedChangeKg == null || requestedChangeKg.signum() == 0
                ? fallback : requestedChangeKg;
        BigDecimal magnitude = requested
                .abs().min(MAX_CHANGE_KG);
        return (goal == Goal.LOSE ? magnitude.negate() : magnitude)
                .setScale(2, RoundingMode.HALF_UP);
    }
}
