package com.example.payment_service.repository;

import com.example.payment_service.model.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    Optional<Subscription> findByCompanyId(UUID companyId);

    // Used in invoice.payment_succeeded / failed / subscription.deleted
    // sub_xxx from Stripe event → find which company it belongs to
    Optional<Subscription> findByStripeSubscriptionId(String stripeSubscriptionId);
}
