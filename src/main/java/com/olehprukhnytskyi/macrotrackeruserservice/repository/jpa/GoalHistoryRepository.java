package com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa;

import com.olehprukhnytskyi.macrotrackeruserservice.model.GoalHistory;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GoalHistoryRepository extends JpaRepository<GoalHistory, Long> {
    @Query("select g from GoalHistory g where g.userId = :userId "
            + "and g.effectiveFrom <= :date and (g.effectiveTo is null or g.effectiveTo >= :date) "
            + "order by g.effectiveFrom desc limit 1")
    Optional<GoalHistory> resolve(@Param("userId") Long userId,
                                  @Param("date") LocalDate date);

    Optional<GoalHistory>
            findFirstByUserIdAndEffectiveToIsNullOrderByEffectiveFromDesc(Long userId);

    Optional<GoalHistory> findFirstByUserIdOrderByEffectiveFromDesc(Long userId);
}
