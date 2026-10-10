package com.olehprukhnytskyi.macrotrackeruserservice.dto;

import java.util.List;

public record WebCatalogDto(boolean available, int yearlyTrialDays, List<WebPriceDto> prices,
                            WebSubscriptionEligibilityDto eligibility) {
}
