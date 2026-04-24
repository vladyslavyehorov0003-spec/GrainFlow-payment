package com.example.payment_service.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "subscriptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Our internal company ID — comes from client_reference_id in checkout.session.completed
    @Column(name = "company_id", nullable = false, unique = true)
    private UUID companyId;

    // Stripe Customer ID (cus_xxx) — needed to open Customer Portal
    @Column(name = "stripe_customer_id", nullable = false)
    private String stripeCustomerId;

    // Stripe Subscription ID (sub_xxx) — the link between checkout and future invoice events
    @Column(name = "stripe_subscription_id", nullable = false, unique = true)
    private String stripeSubscriptionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionStatus status;

    // Unix timestamp — until when the subscription is paid
    @Column(name = "current_period_end")
    private Instant currentPeriodEnd;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
