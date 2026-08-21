package com.olehprukhnytskyi.macrotrackeruserservice.projection;

import com.olehprukhnytskyi.util.ActivityLevel;
import com.olehprukhnytskyi.util.BodyType;
import com.olehprukhnytskyi.util.Gender;
import com.olehprukhnytskyi.util.Goal;
import java.math.BigDecimal;

public interface UserDetailsProjection {
    Integer getAge();

    Integer getWeight();

    Integer getGoalWeight();

    BigDecimal getWeeklyWeightChangeKg();

    Integer getHeight();

    Gender getGender();

    ActivityLevel getActivityLevel();

    Goal getGoal();

    BodyType getBodyType();
}
