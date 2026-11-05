package com.eventhub.registration.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Chamadas HTTP aos serviços Python (payment e notification). Falhas de notificação nunca propagam (RN05). */
@Service
public class ExternalIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(ExternalIntegrationService.class);

    private final RestClient paymentClient;
    private final RestClient notificationClient;
    private final JdbcTemplate jdbcTemplate;
    private final String paymentServiceUrl;
    private final String notificationServiceUrl;

    public ExternalIntegrationService(
            JdbcTemplate jdbcTemplate,
            @Value("${eventhub.downstream.payment-service-url}") String paymentServiceUrl,
            @Value("${eventhub.downstream.notification-service-url}") String notificationServiceUrl,
            @Value("${eventhub.downstream.notification-timeout-ms:1500}") long notificationTimeoutMs
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.paymentServiceUrl = paymentServiceUrl;
        this.notificationServiceUrl = notificationServiceUrl;
        this.paymentClient = clientWithTimeout(Duration.ofSeconds(3));
        this.notificationClient = clientWithTimeout(Duration.ofMillis(notificationTimeoutMs));
    }

    private static RestClient clientWithTimeout(Duration timeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return RestClient.builder().requestFactory(factory).build();
    }

    /** Lança RestClientException em caso de falha; o chamador decide a compensação. */
    public void initiatePayment(Long registrationId, Long userId, BigDecimal amount) {
        paymentClient.post()
                .uri(paymentServiceUrl + "/api/payments/initiate")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("registrationId", registrationId, "userId", userId, "amount", amount))
                .retrieve()
                .toBodilessEntity();
    }

    public void notifyUser(Long userId, String eventType, String message) {
        try {
            List<String> emails = jdbcTemplate.queryForList(
                    "SELECT email FROM users WHERE id = ?", String.class, userId);
            if (emails.isEmpty()) {
                return;
            }
            notificationClient.post()
                    .uri(notificationServiceUrl + "/api/notifications/send")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "userId", userId,
                            "recipientEmail", emails.get(0),
                            "eventType", eventType,
                            "message", message))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            log.warn("Notificação {} não enviada ao usuário {}: {}", eventType, userId, ex.getMessage());
        }
    }
}
