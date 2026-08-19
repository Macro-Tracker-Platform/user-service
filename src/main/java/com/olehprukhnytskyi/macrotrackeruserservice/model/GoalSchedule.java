package com.olehprukhnytskyi.macrotrackeruserservice.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@Entity
@Table(name = "goal_schedules")
@NoArgsConstructor
@AllArgsConstructor
public class GoalSchedule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;
    @Enumerated(EnumType.STRING)
    private DayOfWeek dayOfWeek;
    private Integer calories;
    private Integer protein;
    private Integer fat;
    private Integer carbohydrates;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
}
