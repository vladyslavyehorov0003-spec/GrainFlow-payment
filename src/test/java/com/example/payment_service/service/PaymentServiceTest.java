package com.example.payment_service.service;

import com.example.payment_service.dto.SubscriptionDto;
import com.example.payment_service.kafka.SubscriptionEventProducer;
import com.example.payment_service.repository.SubscriptionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static com.example.payment_service.PaymentTestFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService")
class PaymentServiceTest {

    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private SubscriptionEventProducer eventProducer;
    @InjectMocks private PaymentService paymentService;

    // ===================== getSubscription =====================

    @Test
    @DisplayName("getSubscription: returns DTO when subscription exists")
    void getSubscription_exists_returnsDto() {
        when(subscriptionRepository.findByCompanyId(COMPANY_ID))
                .thenReturn(Optional.of(activeSubscription()));

        SubscriptionDto result = paymentService.getSubscription(COMPANY_ID);

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("ACTIVE");
        assertThat(result.stripeSubscriptionId()).isEqualTo(STRIPE_SUB_ID);
    }

    @Test
    @DisplayName("getSubscription: returns null when no subscription")
    void getSubscription_notFound_returnsNull() {
        when(subscriptionRepository.findByCompanyId(COMPANY_ID)).thenReturn(Optional.empty());

        SubscriptionDto result = paymentService.getSubscription(COMPANY_ID);

        assertThat(result).isNull();
    }

    // ===================== createPortalSession =====================

    @Test
    @DisplayName("createPortalSession: throws when no subscription found")
    void createPortalSession_noSubscription_throws() {
        when(subscriptionRepository.findByCompanyId(COMPANY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.createPortalSession(COMPANY_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No subscription found");
    }

}
