package com.olehprukhnytskyi.macrotrackeruserservice.client;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.GoalResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.UserDetailsRequestDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "goal-service", url = "${feign.goal-service}")
public interface GoalClient {
    @PostMapping("/api/goals")
    GoalResponseDto calculateGoal(@RequestBody UserDetailsRequestDto userDetails);
}
