package com.example.payment_service.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionEventProducer {

    public static final String TOPIC = "subscription.status.changed";

    private final KafkaTemplate<String, SubscriptionStatusEvent> kafkaTemplate;

    public void publish(UUID companyId, String status) {
        SubscriptionStatusEvent event = new SubscriptionStatusEvent(companyId, status);
        kafkaTemplate.send(TOPIC, companyId.toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish subscription event: companyId={} status={} error={}",
                                companyId, status, ex.getMessage());
                    } else {
                        log.info("Subscription event published: companyId={} status={}", companyId, status);
                    }
                });
    }
}
