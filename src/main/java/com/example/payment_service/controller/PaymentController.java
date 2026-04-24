package com.example.payment_service.controller;

import com.example.payment_service.dto.ApiResponse;
import com.example.payment_service.dto.SubscriptionDto;
import com.example.payment_service.security.AuthenticatedUser;
import com.example.payment_service.service.PaymentService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;

    // POST /api/v1/payments/checkout
    @PostMapping("/checkout")
    public ResponseEntity<ApiResponse<Map<String, String>>> createCheckout(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        try {
            String url = paymentService.createCheckoutSession(user.companyId());
            return ResponseEntity.ok(ApiResponse.success(Map.of("url", url)));
        } catch (StripeException e) {
            log.error("Failed to create checkout session: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to create checkout session"));
        }
    }

    // GET /api/v1/payments/subscription
    @GetMapping("/subscription")
    public ResponseEntity<ApiResponse<SubscriptionDto>> getSubscription(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        SubscriptionDto sub = paymentService.getSubscription(user.companyId());
        if (sub == null) {
            return ResponseEntity.ok(ApiResponse.success(null, "No subscription found"));
        }
        return ResponseEntity.ok(ApiResponse.success(sub));
    }

    // POST /api/v1/payments/portal
    @PostMapping("/portal")
    public ResponseEntity<ApiResponse<Map<String, String>>> createPortal(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        try {
            String url = paymentService.createPortalSession(user.companyId());
            return ResponseEntity.ok(ApiResponse.success(Map.of("url", url)));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (StripeException e) {
            log.error("Failed to create portal session: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to create portal session"));
        }
    }

    // POST /api/v1/payments/webhook
    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(
            @RequestHeader("Stripe-Signature") String sigHeader,
            @RequestBody String payload
    ) {
        try {
            paymentService.handleWebhook(payload, sigHeader);
            return ResponseEntity.ok().build();
        } catch (SignatureVerificationException e) {
            return ResponseEntity.badRequest().build();
        } catch (StripeException e) {
            log.error("Webhook processing error: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}
