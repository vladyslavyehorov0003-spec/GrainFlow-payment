package com.example.payment_service.kafka;

import java.util.UUID;

public record SubscriptionStatusEvent(
        UUID companyId,
        String status   // ACTIVE / PAST_DUE / CANCELED
) {}
