package com.olehprukhnytskyi.macrotrackeruserservice.projection;

import com.olehprukhnytskyi.util.ActivityLevel;
import com.olehprukhnytskyi.util.Gender;
import com.olehprukhnytskyi.util.Goal;

public interface UserDetailsProjection {
    Integer getAge();

    Integer getWeight();

    Integer getHeight();

    Gender getGender();

    ActivityLevel getActivityLevel();

    Goal getGoal();
}
