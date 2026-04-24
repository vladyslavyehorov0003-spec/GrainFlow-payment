package com.example.payment_service;

import com.example.payment_service.dto.SubscriptionDto;
import com.example.payment_service.model.Subscription;
import com.example.payment_service.model.SubscriptionStatus;
import com.example.payment_service.security.AuthenticatedUser;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public final class PaymentTestFixtures {

    private PaymentTestFixtures() {}

    public static final UUID COMPANY_ID      = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    public static final UUID USER_ID         = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final String STRIPE_SUB_ID = "sub_test_123";
    public static final String STRIPE_CUS_ID = "cus_test_456";

    public static final AuthenticatedUser manager = new AuthenticatedUser(
            USER_ID, COMPANY_ID, "manager@grainflow.com", "MANAGER"
    );

    public static Subscription activeSubscription() {
        return Subscription.builder()
                .id(UUID.randomUUID())
                .companyId(COMPANY_ID)
                .stripeCustomerId(STRIPE_CUS_ID)
                .stripeSubscriptionId(STRIPE_SUB_ID)
                .status(SubscriptionStatus.ACTIVE)
                .currentPeriodEnd(Instant.now().plus(30, ChronoUnit.DAYS))
                .build();
    }

    public static SubscriptionDto activeSubscriptionDto() {
        return SubscriptionDto.from(activeSubscription());
    }
}
