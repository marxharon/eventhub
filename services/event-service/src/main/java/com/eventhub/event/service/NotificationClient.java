package com.eventhub.event.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Envia notificações ao participante via notification-service. Falhas nunca propagam (RN05). */
@Component
public class NotificationClient {

    private static final Logger log = LoggerFactory.getLogger(NotificationClient.class);

    private final JdbcTemplate jdbcTemplate;
    private final RestClient restClient;
    private final String notificationServiceUrl;

    public NotificationClient(
            JdbcTemplate jdbcTemplate,
            @Value("${eventhub.downstream.notification-service-url}") String notificationServiceUrl,
            @Value("${eventhub.downstream.notification-timeout-ms:1500}") long timeoutMs
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.notificationServiceUrl = notificationServiceUrl;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public void notifyUser(Long userId, String eventType, String message) {
        try {
            List<String> emails = jdbcTemplate.queryForList(
                    "SELECT email FROM users WHERE id = ?", String.class, userId);
            if (emails.isEmpty()) {
                return;
            }
            restClient.post()
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
