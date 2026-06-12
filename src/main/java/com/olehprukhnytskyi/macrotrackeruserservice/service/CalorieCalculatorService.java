package com.olehprukhnytskyi.macrotrackeruserservice.service;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.GoalResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.UserDetailsRequestDto;
import com.olehprukhnytskyi.util.ActivityLevel;
import com.olehprukhnytskyi.util.BodyType;
import com.olehprukhnytskyi.util.Gender;
import com.olehprukhnytskyi.util.Goal;

public class CalorieCalculatorService {
    private static final int CALORIES_PER_GRAM_CARBS = 4;
    private static final int CALORIES_PER_GRAM_FAT = 9;
    private static final int CALORIES_PER_GRAM_PROTEIN = 4;
    private static final int MIN_CALORIES_FEMALE = 1200;
    private static final int MIN_CALORIES_MALE = 1500;
    private static final int MAX_SAFE_PROTEIN = 220;
    private static final int MAX_SAFE_FAT = 110;
    private static final int WATER_ML_PER_KG = 35;
    private static final int WATER_GOAL_ROUNDING_ML = 50;

    public static GoalResponseDto calculateGoal(UserDetailsRequestDto data) {
        double heightMeters = data.getHeight() / 100.0;
        double bmi = data.getWeight() / (heightMeters * heightMeters);
        BodyType effectiveBodyType = resolveBodyType(data.getBodyType(), bmi);

        double bmr = calculateBmr(data, effectiveBodyType, bmi);

        double activityMultiplier = getActivityMultiplier(data.getActivityLevel(), bmi);
        double tdee = bmr * activityMultiplier;

        Goal effectiveGoal = resolveEffectiveGoal(data.getGoal(), bmi, effectiveBodyType);

        int targetCalories = calculateTargetCalories(tdee, effectiveGoal, data.getGender());
        return calculateMacros(data, effectiveBodyType, effectiveGoal, targetCalories);
    }

    private static BodyType resolveBodyType(BodyType originalType, double bmi) {
        if (bmi > 30 && originalType != BodyType.HIGH_BODY_FAT) {
            return BodyType.HIGH_BODY_FAT;
        }
        return originalType;
    }

    private static Goal resolveEffectiveGoal(Goal originalGoal, double bmi,
                                             BodyType bodyType) {
        if (bmi > 40) {
            return Goal.LOSE;
        }
        if (bmi > 35) {
            if (originalGoal == Goal.GAIN) {
                return Goal.MAINTAIN;
            }
            if (bodyType == BodyType.HIGH_BODY_FAT) {
                return Goal.LOSE;
            }
        }
        if (bmi > 30 && bodyType == BodyType.HIGH_BODY_FAT) {
            return Goal.LOSE;
        }
        return originalGoal;
    }

    private static double calculateBmr(UserDetailsRequestDto data, BodyType bodyType,
                                       double bmi) {
        boolean useMifflinStJeor = (bodyType == BodyType.HIGH_BODY_FAT || bmi > 28);

        if (useMifflinStJeor) {
            double baseBmr = (10 * data.getWeight())
                             + (6.25 * data.getHeight()) - (5 * data.getAge());
            return (data.getGender() == Gender.MALE) ? baseBmr + 5 : baseBmr - 161;
        } else {
            double bodyFatPercentage = estimateBodyFat(data.getGender(), bodyType, data.getAge());
            double leanBodyMass = data.getWeight() * (1 - bodyFatPercentage);
            return 370 + (21.6 * leanBodyMass);
        }
    }

    private static int calculateTargetCalories(double tdee, Goal goal, Gender gender) {
        int calories;
        if (goal == Goal.LOSE) {
            calories = (int) (tdee * 0.80);
        } else if (goal == Goal.MAINTAIN) {
            calories = (int) tdee;
        } else {
            calories = (int) (tdee + 300);
        }

        int minAllowed = (gender == Gender.MALE) ? MIN_CALORIES_MALE : MIN_CALORIES_FEMALE;
        return Math.max(calories, minAllowed);
    }

    private static GoalResponseDto calculateMacros(UserDetailsRequestDto data, BodyType bodyType,
                                                   Goal goal, int targetCalories) {
        double bodyFatPct = estimateBodyFat(data.getGender(), bodyType, data.getAge());
        double leanMass = data.getWeight() * (1 - bodyFatPct);
        double proteinMultiplier = getProteinMultiplier(bodyType, goal);

        int protein = (int) (leanMass * proteinMultiplier);
        protein = Math.min(protein, MAX_SAFE_PROTEIN);

        int fat = (int) (data.getWeight() * 0.9);
        fat = Math.min(fat, MAX_SAFE_FAT);
        fat = Math.max(fat, 50);

        int caloriesFromProteinAndFat = (protein * CALORIES_PER_GRAM_PROTEIN)
                                        + (fat * CALORIES_PER_GRAM_FAT);
        int remainingCalories = targetCalories - caloriesFromProteinAndFat;

        if (remainingCalories < 0) {
            targetCalories = caloriesFromProteinAndFat;
            remainingCalories = 0;
        }
        int carbs = remainingCalories / CALORIES_PER_GRAM_CARBS;
        int waterGoalMl = calculateWaterGoal(data.getWeight());
        return new GoalResponseDto(targetCalories, protein, fat, carbs, waterGoalMl);
    }

    private static int calculateWaterGoal(int weightKg) {
        int unroundedGoal = weightKg * WATER_ML_PER_KG;
        return Math.round((float) unroundedGoal / WATER_GOAL_ROUNDING_ML)
               * WATER_GOAL_ROUNDING_ML;
    }

    private static double getProteinMultiplier(BodyType bodyType, Goal goal) {
        if (bodyType == BodyType.HIGH_BODY_FAT) {
            return (goal == Goal.MAINTAIN) ? 1.4 : 1.6;
        }
        if (bodyType == BodyType.LEAN) {
            return (goal == Goal.MAINTAIN) ? 2.0 : 2.4;
        }
        return (goal == Goal.MAINTAIN) ? 1.8 : 2.0;
    }

    private static double getActivityMultiplier(ActivityLevel level, double bmi) {
        double multiplier;
        if (level == ActivityLevel.SEDENTARY) {
            multiplier = 1.2;
        } else if (level == ActivityLevel.LIGHTLY_ACTIVE) {
            multiplier = 1.375;
        } else if (level == ActivityLevel.MODERATELY_ACTIVE) {
            multiplier = 1.550;
        } else if (level == ActivityLevel.VERY_ACTIVE) {
            multiplier = 1.725;
        } else {
            multiplier = 1.9;
        }
        if (bmi > 35) {
            multiplier = Math.min(multiplier, 1.5);
        }
        return multiplier;
    }

    private static double estimateBodyFat(Gender gender, BodyType bodyType, int age) {
        double baseFat;
        if (gender == Gender.MALE) {
            if (bodyType == BodyType.LEAN) {
                baseFat = 0.1;
            } else if (bodyType == BodyType.NORMAL) {
                baseFat = 0.15;
            } else {
                baseFat = 0.3;
            }
        } else {
            if (bodyType == BodyType.LEAN) {
                baseFat = 0.18;
            } else if (bodyType == BodyType.NORMAL) {
                baseFat = 0.25;
            } else {
                baseFat = 0.4;
            }
        }
        if (age > 30) {
            baseFat += (age - 30) * 0.001;
        }
        return baseFat;
    }
}
