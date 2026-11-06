package com.eventhub.event.service;

import com.eventhub.event.config.RabbitMqConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Envia notificações ao participante via Publish/Subscribe Assíncrono (RabbitMQ) pós-commit,
 * com fallback HTTP protegido por Circuit Breaker (Resilience4j). Falhas nunca propagam (RN05).
 */
@Component
public class NotificationClient {

    private static final Logger log = LoggerFactory.getLogger(NotificationClient.class);

    private final JdbcTemplate jdbcTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final NotificationHttpFallbackClient notificationHttpFallbackClient;
    private final boolean messagingEnabled;
    private final String exchangeName;

    public NotificationClient(
            JdbcTemplate jdbcTemplate,
            RabbitTemplate rabbitTemplate,
            NotificationHttpFallbackClient notificationHttpFallbackClient,
            @Value("${eventhub.messaging.enabled:true}") boolean messagingEnabled,
            @Value("${eventhub.messaging.exchange:" + RabbitMqConfig.DOMAIN_EXCHANGE + "}") String exchangeName
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.rabbitTemplate = rabbitTemplate;
        this.notificationHttpFallbackClient = notificationHttpFallbackClient;
        this.messagingEnabled = messagingEnabled;
        this.exchangeName = exchangeName;
    }

    public void notifyUser(Long userId, String eventType, String message) {
        List<String> emails = jdbcTemplate.queryForList(
                "SELECT email FROM users WHERE id = ?", String.class, userId);
        if (emails.isEmpty()) {
            return;
        }
        String recipientEmail = emails.get(0);

        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatchNotification(userId, recipientEmail, eventType, message);
                }
            });
        } else {
            dispatchNotification(userId, recipientEmail, eventType, message);
        }
    }

    private void dispatchNotification(Long userId, String recipientEmail, String eventType, String message) {
        Map<String, Object> payload = Map.of(
                "userId", userId,
                "recipientEmail", recipientEmail,
                "eventType", eventType,
                "message", message
        );

        if (messagingEnabled) {
            String routingKey = "notification.event." + eventType.toLowerCase(Locale.ROOT);
            Map<String, Object> envelope = Map.of(
                    "eventId", UUID.randomUUID().toString(),
                    "occurredAt", Instant.now().toString(),
                    "sourceService", "event-service",
                    "routingKey", routingKey,
                    "payload", payload
            );
            try {
                rabbitTemplate.convertAndSend(exchangeName, routingKey, envelope);
                log.info("Evento {} publicado no RabbitMQ ({}) pós-commit para userId={}", eventType, routingKey, userId);
                return;
            } catch (Exception amqpEx) {
                log.warn("Broker AMQP indisponível ({}); acionando fallback HTTP com CircuitBreaker para {}",
                        amqpEx.getMessage(), eventType);
            }
        }

        notificationHttpFallbackClient.sendHttp(userId, recipientEmail, eventType, message);
    }
}
