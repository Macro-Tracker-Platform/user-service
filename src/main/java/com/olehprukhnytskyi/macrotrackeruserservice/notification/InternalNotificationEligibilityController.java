package com.olehprukhnytskyi.macrotrackeruserservice.notification;

import com.olehprukhnytskyi.macrotrackeruserservice.service.AiCreditService;
import com.olehprukhnytskyi.util.CustomHeaders;
import java.time.LocalDate;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/users")
public class InternalNotificationEligibilityController {
    private final AiCreditService aiCreditService;

    @GetMapping("/ai-credits/exhausted")
    public ResponseEntity<Map<String, Boolean>> wasAiLimitExhausted(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "UTC") String timeZone) {
        return ResponseEntity.ok(Map.of(
                "exhausted", aiCreditService.wasExhausted(
                        userId, date, java.time.ZoneId.of(timeZone))));
    }
}
