package com.olehprukhnytskyi.macrotrackeruserservice.projection;

import com.olehprukhnytskyi.macrotrackeruserservice.util.ActivityLevel;
import com.olehprukhnytskyi.macrotrackeruserservice.util.Gender;
import com.olehprukhnytskyi.macrotrackeruserservice.util.Goal;

public interface UserDetailsProjection {
    Integer getAge();

    Integer getWeight();

    Integer getHeight();

    Gender getGender();

    ActivityLevel getActivityLevel();

    Goal getGoal();
}
