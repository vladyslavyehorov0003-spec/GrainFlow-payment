package com.example.payment_service.service;

import com.example.payment_service.dto.SubscriptionDto;
import com.example.payment_service.kafka.SubscriptionEventProducer;
import com.example.payment_service.model.SubscriptionStatus;
import com.example.payment_service.repository.SubscriptionRepository;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionEventProducer eventProducer;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    @Value("${stripe.price-id}")
    private String priceId;

    @Value("${frontend.url}")
    private String frontendUrl;

    public String createCheckoutSession(UUID companyId) throws StripeException {
        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setClientReferenceId(companyId.toString())
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setPrice(priceId)
                        .setQuantity(1L)
                        .build())
                .setSubscriptionData(SessionCreateParams.SubscriptionData.builder()
                        .setTrialPeriodDays(14L)
                        .build())
                .setSuccessUrl(frontendUrl + "/app?payment=success")
                .setCancelUrl(frontendUrl + "/app?payment=canceled")
                .build();

        Session session = Session.create(params);
        log.info("Checkout session created: company={} session={}", companyId, session.getId());
        return session.getUrl();
    }

    public void handleWebhook(String payload, String sigHeader) throws StripeException {
        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.warn("Invalid Stripe webhook signature: {}", e.getMessage());
            throw e;
        }

        log.info("Stripe event received: type={} id={}", event.getType(), event.getId());

        switch (event.getType()) {
            case "checkout.session.completed"    -> handleCheckoutCompleted(event);
            case "invoice.payment_succeeded"     -> handlePaymentSucceeded(event);
            case "invoice.payment_failed"        -> handlePaymentFailed(event);
            case "customer.subscription.deleted" -> handleSubscriptionDeleted(event);
            default -> log.debug("Ignored event type: {}", event.getType());
        }
    }

    // Step 1 — user paid for the first time.
    // Stripe returns the companyId we embedded + gives us customerId and subscriptionId.
    // We save all three to DB — this creates the link between our company and Stripe.
    @Transactional
    protected void handleCheckoutCompleted(Event event) {
        Session session;
        try {
            session = (Session) event.getDataObjectDeserializer().deserializeUnsafe();
        } catch (Exception e) {
            log.error("Could not deserialize Session from event {}: {}", event.getId(), e.getMessage());
            return;
        }

        UUID companyId              = UUID.fromString(session.getClientReferenceId());
        String stripeCustomerId     = session.getCustomer();
        String stripeSubscriptionId = session.getSubscription();

        log.info("[checkout.session.completed] companyId={} customer={} subscription={}",
                companyId, stripeCustomerId, stripeSubscriptionId);

        com.example.payment_service.model.Subscription subscription =
                com.example.payment_service.model.Subscription.builder()
                        .companyId(companyId)
                        .stripeCustomerId(stripeCustomerId)
                        .stripeSubscriptionId(stripeSubscriptionId)
                        .status(SubscriptionStatus.ACTIVE)
                        .build();

        subscriptionRepository.save(subscription);
        log.info("Subscription saved to DB: companyId={}", companyId);
        eventProducer.publish(companyId, SubscriptionStatus.ACTIVE.name());
    }

    // Step 2 — monthly payment succeeded.
    // invoice.parent.subscription_details.subscription is the sub_xxx that links back to our DB record.
    @Transactional
    protected void handlePaymentSucceeded(Event event) {
        Invoice invoice;
        try {
            invoice = (Invoice) event.getDataObjectDeserializer().deserializeUnsafe();
        } catch (Exception e) {
            log.error("Could not deserialize Invoice from event {}: {}", event.getId(), e.getMessage());
            return;
        }

        String stripeSubscriptionId = extractSubscriptionId(invoice);
        Long periodEnd              = invoice.getPeriodEnd();

        if (stripeSubscriptionId == null) {
            log.debug("[invoice.payment_succeeded] subscriptionId is null, skipping");
            return;
        }

        subscriptionRepository.findByStripeSubscriptionId(stripeSubscriptionId)
                .ifPresentOrElse(sub -> {
                    sub.setStatus(SubscriptionStatus.ACTIVE);
                    if (periodEnd != null) {
                        sub.setCurrentPeriodEnd(Instant.ofEpochSecond(periodEnd));
                    }
                    subscriptionRepository.save(sub);
                    eventProducer.publish(sub.getCompanyId(), SubscriptionStatus.ACTIVE.name());
                    log.info("[invoice.payment_succeeded] companyId={} sub={} active until={}",
                            sub.getCompanyId(), stripeSubscriptionId, periodEnd);
                }, () -> log.debug("[invoice.payment_succeeded] subscription not found in DB: {} (expected on first payment)", stripeSubscriptionId));
    }

    // Monthly payment failed — Stripe will retry, but we mark as PAST_DUE.
    @Transactional
    protected void handlePaymentFailed(Event event) {
        Invoice invoice;
        try {
            invoice = (Invoice) event.getDataObjectDeserializer().deserializeUnsafe();
        } catch (Exception e) {
            log.error("Could not deserialize Invoice from event {}: {}", event.getId(), e.getMessage());
            return;
        }

        String stripeSubscriptionId = extractSubscriptionId(invoice);

        if (stripeSubscriptionId == null) {
            log.debug("[invoice.payment_failed] subscriptionId is null, skipping");
            return;
        }

        subscriptionRepository.findByStripeSubscriptionId(stripeSubscriptionId)
                .ifPresentOrElse(sub -> {
                    sub.setStatus(SubscriptionStatus.PAST_DUE);
                    subscriptionRepository.save(sub);
                    eventProducer.publish(sub.getCompanyId(), SubscriptionStatus.PAST_DUE.name());
                    log.info("[invoice.payment_failed] companyId={} marked as PAST_DUE", sub.getCompanyId());
                }, () -> log.warn("[invoice.payment_failed] subscription not found in DB: {}", stripeSubscriptionId));
    }

    // Subscription fully canceled — company loses access.
    @Transactional
    protected void handleSubscriptionDeleted(Event event) {
        com.stripe.model.Subscription stripeSubscription;
        try {
            stripeSubscription = (com.stripe.model.Subscription) event.getDataObjectDeserializer().deserializeUnsafe();
        } catch (Exception e) {
            log.error("Could not deserialize Subscription from event {}: {}", event.getId(), e.getMessage());
            return;
        }

        String stripeSubscriptionId = stripeSubscription.getId();

        subscriptionRepository.findByStripeSubscriptionId(stripeSubscriptionId)
                .ifPresentOrElse(sub -> {
                    sub.setStatus(SubscriptionStatus.CANCELED);
                    subscriptionRepository.save(sub);
                    eventProducer.publish(sub.getCompanyId(), SubscriptionStatus.CANCELED.name());
                    log.info("[customer.subscription.deleted] companyId={} CANCELED", sub.getCompanyId());
                }, () -> log.warn("[customer.subscription.deleted] subscription not found in DB: {}", stripeSubscriptionId));
    }

    public SubscriptionDto getSubscription(UUID companyId) {
        return subscriptionRepository.findByCompanyId(companyId)
                .map(SubscriptionDto::from)
                .orElse(null);
    }

    public String createPortalSession(UUID companyId) throws StripeException {
        com.example.payment_service.model.Subscription sub = subscriptionRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new IllegalStateException("No subscription found for company: " + companyId));

        com.stripe.param.billingportal.SessionCreateParams params =
                com.stripe.param.billingportal.SessionCreateParams.builder()
                        .setCustomer(sub.getStripeCustomerId())
                        .setReturnUrl(frontendUrl + "/app")
                        .build();

        com.stripe.model.billingportal.Session portalSession =
                com.stripe.model.billingportal.Session.create(params);

        log.info("Portal session created: companyId={}", companyId);
        return portalSession.getUrl();
    }

    // Extracts subscription ID from invoice.parent.subscription_details.subscription (Stripe API 2026+)
    private String extractSubscriptionId(Invoice invoice) {
        if (invoice.getParent() == null) return null;
        var subDetails = invoice.getParent().getSubscriptionDetails();
        if (subDetails == null) return null;
        return subDetails.getSubscription();
    }

}
