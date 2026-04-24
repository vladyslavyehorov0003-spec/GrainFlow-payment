package com.example.payment_service.kafka;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionEventProducer")
class SubscriptionEventProducerTest {

    @Mock private KafkaTemplate<String, SubscriptionStatusEvent> kafkaTemplate;
    @InjectMocks private SubscriptionEventProducer producer;

    @Test
    @DisplayName("publish: sends event to correct topic with companyId as key")
    void publish_sendsEventToTopic() {
        UUID companyId = UUID.randomUUID();
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));

        producer.publish(companyId, "ACTIVE");

        ArgumentCaptor<SubscriptionStatusEvent> captor = ArgumentCaptor.forClass(SubscriptionStatusEvent.class);
        verify(kafkaTemplate).send(
                eq(SubscriptionEventProducer.TOPIC),
                eq(companyId.toString()),
                captor.capture()
        );

        assertThat(captor.getValue().companyId()).isEqualTo(companyId);
        assertThat(captor.getValue().status()).isEqualTo("ACTIVE");
    }
}
