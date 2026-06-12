package com.olehprukhnytskyi.macrotrackeruserservice.projection;

import com.olehprukhnytskyi.macrotrackeruserservice.util.WaterGoalMode;

public interface UserGoalProjection {
    Integer getCalories();

    Integer getCarbohydrates();

    Integer getFat();

    Integer getProtein();

    Integer getWaterGoalMl();

    WaterGoalMode getWaterGoalMode();
}
