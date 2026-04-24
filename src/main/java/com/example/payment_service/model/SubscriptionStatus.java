package com.example.payment_service.model;

public enum SubscriptionStatus {
    ACTIVE,      // payment succeeded — company has access
    PAST_DUE,    // payment failed — Stripe is retrying, company still has temporary access
    CANCELED,    // subscription deleted — company loses access
    INACTIVE     // no subscription at all
}
