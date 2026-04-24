package com.example.payment_service.controller;

import com.example.payment_service.config.SecurityConfig;
import com.example.payment_service.security.AuthClient;
import com.example.payment_service.security.ValidateResponse;
import com.example.payment_service.service.PaymentService;
import com.stripe.exception.SignatureVerificationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static com.example.payment_service.PaymentTestFixtures.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentController.class)
@Import(SecurityConfig.class)
@DisplayName("PaymentController")
class PaymentControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean PaymentService paymentService;
    @MockitoBean AuthClient authClient;

    @BeforeEach
    void setUp(WebApplicationContext wac) {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(wac)
                .apply(springSecurity())
                .defaultRequest(get("/").contextPath("/api/v1"))
                .build();
    }

    // ===================== CHECKOUT =====================

    @Test
    @DisplayName("POST /checkout → 200 with url")
    void checkout_asManager_returns200() throws Exception {
        when(paymentService.createCheckoutSession(COMPANY_ID))
                .thenReturn("https://checkout.stripe.com/test");

        mockMvc.perform(post("/api/v1/payments/checkout").with(user(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.url").value("https://checkout.stripe.com/test"));
    }

    @Test
    @DisplayName("POST /checkout unauthenticated → 403")
    void checkout_unauthenticated_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/payments/checkout"))
                .andExpect(status().isForbidden());
    }

    // ===================== SUBSCRIPTION =====================

    @Test
    @DisplayName("GET /subscription → 200 with data when subscription exists")
    void subscription_exists_returns200WithData() throws Exception {
        when(paymentService.getSubscription(COMPANY_ID)).thenReturn(activeSubscriptionDto());

        mockMvc.perform(get("/api/v1/payments/subscription").with(user(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.stripeSubscriptionId").value(STRIPE_SUB_ID));
    }

    @Test
    @DisplayName("GET /subscription → 200 with null data when no subscription")
    void subscription_notFound_returns200WithNull() throws Exception {
        when(paymentService.getSubscription(COMPANY_ID)).thenReturn(null);

        mockMvc.perform(get("/api/v1/payments/subscription").with(user(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    // ===================== PORTAL =====================

    @Test
    @DisplayName("POST /portal → 200 with url when subscription exists")
    void portal_subscriptionExists_returns200() throws Exception {
        when(paymentService.createPortalSession(COMPANY_ID))
                .thenReturn("https://billing.stripe.com/test");

        mockMvc.perform(post("/api/v1/payments/portal").with(user(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.url").value("https://billing.stripe.com/test"));
    }

    @Test
    @DisplayName("POST /portal → 400 when no subscription found")
    void portal_noSubscription_returns400() throws Exception {
        when(paymentService.createPortalSession(COMPANY_ID))
                .thenThrow(new IllegalStateException("No subscription found for company: " + COMPANY_ID));

        mockMvc.perform(post("/api/v1/payments/portal").with(user(manager)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));
    }

    // ===================== WEBHOOK =====================

    @Test
    @DisplayName("POST /webhook → 200 with valid signature")
    void webhook_validSignature_returns200() throws Exception {
        doNothing().when(paymentService).handleWebhook(any(), any());

        mockMvc.perform(post("/api/v1/payments/webhook")
                        .header("Stripe-Signature", "t=123,v1=abc")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{\"type\":\"checkout.session.completed\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /webhook → 400 with invalid signature")
    void webhook_invalidSignature_returns400() throws Exception {
        doThrow(new SignatureVerificationException("Invalid signature", "sig"))
                .when(paymentService).handleWebhook(any(), any());

        mockMvc.perform(post("/api/v1/payments/webhook")
                        .header("Stripe-Signature", "invalid")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /webhook → accessible without auth")
    void webhook_noAuth_isAccessible() throws Exception {
        doNothing().when(paymentService).handleWebhook(any(), any());

        mockMvc.perform(post("/api/v1/payments/webhook")
                        .header("Stripe-Signature", "t=123,v1=abc")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{}"))
                .andExpect(status().isOk());
    }
}
