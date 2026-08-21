package com.olehprukhnytskyi.macrotrackeruserservice.controller;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.AdaptiveCalorieEvaluationRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.AdaptiveCalorieRecommendationDto;
import com.olehprukhnytskyi.macrotrackeruserservice.service.AdaptiveCalorieCalculatorService;
import com.olehprukhnytskyi.util.CustomHeaders;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/profile")
public class InternalAdaptiveCalorieController {
    private final AdaptiveCalorieCalculatorService adaptiveCalorieCalculatorService;

    @PostMapping("/adaptive-calories")
    public ResponseEntity<AdaptiveCalorieRecommendationDto> evaluate(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId,
            @RequestBody AdaptiveCalorieEvaluationRequestDto request) {
        return ResponseEntity.ok(adaptiveCalorieCalculatorService.evaluate(userId, request));
    }
}
