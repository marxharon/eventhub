package com.eventhub.registration.service;

import com.eventhub.registration.config.RabbitMqConfig;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Integração externa do registration-service com suporte a Publish/Subscribe Assíncrono (RabbitMQ)
 * disparado pós-commit (AFTER_COMMIT) e Fallback HTTP protegido por Circuit Breaker (Resilience4j).
 */
@Service
public class ExternalIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(ExternalIntegrationService.class);

    private final RestClient paymentClient;
    private final JdbcTemplate jdbcTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final NotificationHttpFallbackClient notificationHttpFallbackClient;
    private final String paymentServiceUrl;
    private final boolean messagingEnabled;
    private final String exchangeName;

    public ExternalIntegrationService(
            JdbcTemplate jdbcTemplate,
            RabbitTemplate rabbitTemplate,
            NotificationHttpFallbackClient notificationHttpFallbackClient,
            @Value("${eventhub.downstream.payment-service-url}") String paymentServiceUrl,
            @Value("${eventhub.messaging.enabled:true}") boolean messagingEnabled,
            @Value("${eventhub.messaging.exchange:" + RabbitMqConfig.DOMAIN_EXCHANGE + "}") String exchangeName
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.rabbitTemplate = rabbitTemplate;
        this.notificationHttpFallbackClient = notificationHttpFallbackClient;
        this.paymentServiceUrl = paymentServiceUrl;
        this.messagingEnabled = messagingEnabled;
        this.exchangeName = exchangeName;
        this.paymentClient = clientWithTimeout(Duration.ofSeconds(3));
    }

    private static RestClient clientWithTimeout(Duration timeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return RestClient.builder().requestFactory(factory).build();
    }

    /** Lança RestClientException em caso de falha ou circuito aberto; o chamador decide a compensação. */
    @CircuitBreaker(name = "paymentServiceCB", fallbackMethod = "initiatePaymentFallback")
    @Retry(name = "paymentServiceRetry")
    public void initiatePayment(Long registrationId, Long userId, BigDecimal amount) {
        paymentClient.post()
                .uri(paymentServiceUrl + "/api/payments/initiate")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("registrationId", registrationId, "userId", userId, "amount", amount))
                .retrieve()
                .toBodilessEntity();
    }

    @SuppressWarnings("unused")
    private void initiatePaymentFallback(Long registrationId, Long userId, BigDecimal amount, Throwable t) {
        log.warn("CircuitBreaker/Fallback acionado em initiatePayment para inscrição {}: {}", registrationId, t.getMessage());
        if (t instanceof RestClientException rce) {
            throw rce;
        }
        throw new RestClientException("CircuitBreaker paymentServiceCB ativo: " + t.getMessage(), t);
    }

    /** Encerra pagamentos PENDING associados a uma inscrição cancelada/expirada. Falhas não propagam. */
    @CircuitBreaker(name = "paymentServiceCB", fallbackMethod = "cancelPendingPaymentFallback")
    public void cancelPendingPayment(Long registrationId, String reasonStatus) {
        paymentClient.post()
                .uri(paymentServiceUrl + "/api/payments/registration/" + registrationId + "/cancel-pending?status=" + reasonStatus)
                .retrieve()
                .toBodilessEntity();
    }

    @SuppressWarnings("unused")
    private void cancelPendingPaymentFallback(Long registrationId, String reasonStatus, Throwable t) {
        log.warn("Pagamento pendente da inscrição {} não encerrado remotamente (CircuitBreaker/Fallback): {}",
                registrationId, t.getMessage());
    }

    /**
     * Dispara a notificação fora do lock pessimista (em AFTER_COMMIT quando há transação ativa),
     * publicando via RabbitMQ com fallback automático para HTTP + Circuit Breaker (RN05).
     */
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
            String routingKey = "notification.registration." + eventType.toLowerCase(Locale.ROOT);
            Map<String, Object> envelope = Map.of(
                    "eventId", UUID.randomUUID().toString(),
                    "occurredAt", Instant.now().toString(),
                    "sourceService", "registration-service",
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
