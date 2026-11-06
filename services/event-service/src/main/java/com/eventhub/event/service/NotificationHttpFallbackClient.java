package com.eventhub.event.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

/**
 * Fallback HTTP protegido por Circuit Breaker (Resilience4j) para envio de notificações
 * quando o broker RabbitMQ estiver desabilitado ou indisponível.
 */
@Component
public class NotificationHttpFallbackClient {

    private static final Logger log = LoggerFactory.getLogger(NotificationHttpFallbackClient.class);

    private final RestClient restClient;
    private final String notificationServiceUrl;

    public NotificationHttpFallbackClient(
            @Value("${eventhub.downstream.notification-service-url}") String notificationServiceUrl,
            @Value("${eventhub.downstream.notification-timeout-ms:1500}") long timeoutMs
    ) {
        this.notificationServiceUrl = notificationServiceUrl;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @CircuitBreaker(name = "notificationServiceCB", fallbackMethod = "sendHttpFallback")
    public void sendHttp(Long userId, String recipientEmail, String eventType, String message) {
        restClient.post()
                .uri(notificationServiceUrl + "/api/notifications/send")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "userId", userId,
                        "recipientEmail", recipientEmail,
                        "eventType", eventType,
                        "message", message))
                .retrieve()
                .toBodilessEntity();
    }

    @SuppressWarnings("unused")
    private void sendHttpFallback(Long userId, String recipientEmail, String eventType, String message, Throwable t) {
        log.warn("Notificação {} não enviada ao usuário {} (CircuitBreaker/Fallback): {}",
                eventType, userId, t.getMessage());
    }
}
