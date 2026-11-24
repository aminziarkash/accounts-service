package com.az.notifications;

import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.RetryListener;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaErrorHandlingConfig {

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> template, MeterRegistry registry) {
        // partition -1: let the producer pick by key, so the DLT does not need the same partition count
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                template, (record, exception) -> new TopicPartition(record.topic() + ".DLT", -1));

        // 3 retries, one second apart, then the recoverer publishes to the DLT
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L));

        // a missing header or a bad UUID fails the same way every time: no retries
        handler.addNotRetryableExceptions(IllegalArgumentException.class, OutOfOrderEventException.class);

        handler.setRetryListeners(new RetryListener() {
            @Override
            public void failedDelivery(
                    org.apache.kafka.clients.consumer.ConsumerRecord<?, ?> record, Exception ex, int deliveryAttempt) {
                LoggerFactory.getLogger("dead-letter")
                        .warn("Delivery attempt {} failed for partition={} offset={}: {}",
                                deliveryAttempt, record.partition(), record.offset(), ex.toString());
            }

            @Override
            public void recovered(org.apache.kafka.clients.consumer.ConsumerRecord<?, ?> record, Exception ex) {
                LoggerFactory.getLogger("dead-letter")
                        .warn("Dead-lettered record partition={} offset={}: {}", record.partition(), record.offset(), ex.toString());
                registry.counter("notifications.events.dead.lettered", "reason", ex.getCause() != null
                        ? ex.getCause().getClass().getSimpleName() : ex.getClass().getSimpleName()).increment();
            }
        });

        return handler;
    }
}