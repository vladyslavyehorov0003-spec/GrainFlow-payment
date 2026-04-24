package com.example.payment_service.config;

import com.example.payment_service.kafka.SubscriptionEventProducer;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic subscriptionStatusChangedTopic() {
        return TopicBuilder.name(SubscriptionEventProducer.TOPIC)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
