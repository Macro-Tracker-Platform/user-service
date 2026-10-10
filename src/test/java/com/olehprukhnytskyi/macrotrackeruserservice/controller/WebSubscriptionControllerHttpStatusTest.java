package com.olehprukhnytskyi.macrotrackeruserservice.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.olehprukhnytskyi.exception.GlobalExceptionHandler;
import com.olehprukhnytskyi.macrotrackeruserservice.exception.HttpStatusExceptionHandler;
import com.olehprukhnytskyi.macrotrackeruserservice.service.PromoCodeService;
import com.olehprukhnytskyi.macrotrackeruserservice.service.WebCheckoutService;
import com.olehprukhnytskyi.macrotrackeruserservice.service.WebStorefrontService;
import com.olehprukhnytskyi.util.CustomHeaders;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

class WebSubscriptionControllerHttpStatusTest {
    @ParameterizedTest
    @CsvSource({
            "CONFLICT, SUBSCRIPTION_RECONCILIATION_REQUIRED",
            "SERVICE_UNAVAILABLE, WEB_BILLING_NOT_CONFIGURED"
    })
    void billingErrorsKeepStatusAndMachineCodeWithTheCommonAdvicePresent(
            HttpStatus expectedStatus, String code) throws Exception {
        var storefront = mock(WebStorefrontService.class);
        when(storefront.catalog(42L, "USER"))
                .thenThrow(new ResponseStatusException(expectedStatus, code));
        var controller = new WebSubscriptionController(storefront,
                mock(PromoCodeService.class), mock(WebCheckoutService.class));
        var mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(), new HttpStatusExceptionHandler())
                .build();
        mvc.perform(get("/api/subscriptions/web/catalog")
                .header(CustomHeaders.X_USER_ID, "42")
                .header(CustomHeaders.X_USER_ROLES, "USER"))
                .andExpect(status().is(expectedStatus.value()))
                .andExpect(jsonPath("$.status").value(expectedStatus.value()))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.detail").value(code))
                .andExpect(header().string("Cache-Control", "no-store"));
    }
}
