package com.example.payment_service.dto;

import com.example.payment_service.model.Subscription;

import java.time.Instant;

public record SubscriptionDto(
        String status,
        Instant currentPeriodEnd,
        String stripeSubscriptionId
) {
    public static SubscriptionDto from(Subscription sub) {
        return new SubscriptionDto(
                sub.getStatus().name(),
                sub.getCurrentPeriodEnd(),
                sub.getStripeSubscriptionId()
        );
    }
}
