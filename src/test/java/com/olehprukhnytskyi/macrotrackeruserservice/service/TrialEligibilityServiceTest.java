package com.olehprukhnytskyi.macrotrackeruserservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.olehprukhnytskyi.macrotrackeruserservice.model.Subscription;
import com.olehprukhnytskyi.macrotrackeruserservice.model.SubscriptionTrialRedemption;
import com.olehprukhnytskyi.macrotrackeruserservice.properties.GooglePlayProperties;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.SubscriptionTrialRedemptionRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class TrialEligibilityServiceTest {
    private static final Long USER_ID = 42L;
    private static final String TRIAL_OFFER_ID = "yearly-14-day-trial";

    @Mock
    private SubscriptionTrialRedemptionRepository redemptionRepository;

    private TrialEligibilityService service;

    @BeforeEach
    void setUp() {
        GooglePlayProperties properties = new GooglePlayProperties();
        properties.getTrialOfferIds().add(TRIAL_OFFER_ID);
        service = new TrialEligibilityService(redemptionRepository, properties);
    }

    @Test
    void firstTrialIsRecordedPermanently() {
        Subscription subscription = Subscription.builder().id(7L).build();
        when(redemptionRepository.findById(USER_ID)).thenReturn(Optional.empty());

        service.recordRedemption(USER_ID, subscription, TRIAL_OFFER_ID, true);

        ArgumentCaptor<SubscriptionTrialRedemption> redemption =
                ArgumentCaptor.forClass(SubscriptionTrialRedemption.class);
        verify(redemptionRepository).save(redemption.capture());
        assertThat(redemption.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(redemption.getValue().getSubscription()).isEqualTo(subscription);
        assertThat(redemption.getValue().getOfferId()).isEqualTo(TRIAL_OFFER_ID);
    }

    @Test
    void secondTrialForSameUserIsRejected() {
        when(redemptionRepository.findById(USER_ID)).thenReturn(Optional.of(
                SubscriptionTrialRedemption.builder().userId(USER_ID).build()));

        assertThatThrownBy(() -> service.recordRedemption(
                USER_ID, Subscription.builder().id(8L).build(), TRIAL_OFFER_ID, true))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already been used");
    }

    @Test
    void normalDiscountOfferDoesNotConsumeTrial() {
        service.recordRedemption(
                USER_ID, Subscription.builder().id(7L).build(), "discount-only", true);

        verify(redemptionRepository, never()).findById(USER_ID);
        verify(redemptionRepository, never()).save(
                org.mockito.ArgumentMatchers.any());
    }
}
