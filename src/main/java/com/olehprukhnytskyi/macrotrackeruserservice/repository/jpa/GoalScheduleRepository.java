package com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa;

import com.olehprukhnytskyi.macrotrackeruserservice.model.GoalSchedule;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GoalScheduleRepository extends JpaRepository<GoalSchedule, Long> {
    List<GoalSchedule> findByUserIdAndEffectiveToIsNullOrderByDayOfWeek(Long userId);

    @Query("select g from GoalSchedule g where g.userId = :userId "
            + "and g.dayOfWeek = :dayOfWeek and g.effectiveFrom <= :date "
            + "and (g.effectiveTo is null or g.effectiveTo >= :date) "
            + "order by g.effectiveFrom desc limit 1")
    Optional<GoalSchedule> resolve(@Param("userId") Long userId,
                                   @Param("dayOfWeek") DayOfWeek dayOfWeek,
                                   @Param("date") LocalDate date);

    Optional<GoalSchedule>
            findFirstByUserIdAndDayOfWeekAndEffectiveToIsNullOrderByEffectiveFromDesc(
                    Long userId, DayOfWeek dayOfWeek);
}
