package com.eventhub.event.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Cliente de integração com o registration-service protegido por Circuit Breaker (Resilience4j).
 * Caso o registration-service esteja indisponível ou com circuito aberto, executa o fallback local via JdbcTemplate.
 */
@Component
public class RegistrationIntegrationClient {

    private static final Logger log = LoggerFactory.getLogger(RegistrationIntegrationClient.class);

    private final RestClient registrationClient;
    private final JdbcTemplate jdbcTemplate;
    private final String registrationServiceUrl;

    public RegistrationIntegrationClient(
            JdbcTemplate jdbcTemplate,
            @Value("${eventhub.downstream.registration-service-url:http://registration-service:8084}") String registrationServiceUrl
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.registrationServiceUrl = registrationServiceUrl;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(2));
        this.registrationClient = RestClient.builder().requestFactory(factory).build();
    }

    @CircuitBreaker(name = "registrationServiceCB", fallbackMethod = "cancelAllLocalFallback")
    public void cancelAllByEvent(Long eventId, String authorizationHeader) {
        var requestSpec = registrationClient.post()
                .uri(registrationServiceUrl + "/api/registrations/event/" + eventId + "/cancel-all");
        if (authorizationHeader != null && !authorizationHeader.isBlank()) {
            requestSpec = requestSpec.header("Authorization", authorizationHeader);
        }
        requestSpec.retrieve().toBodilessEntity();
    }

    @SuppressWarnings("unused")
    private void cancelAllLocalFallback(Long eventId, String authorizationHeader, Throwable t) {
        log.warn("Fallback local (CircuitBreaker registrationServiceCB) ao cancelar inscricoes do evento {}: {}",
                eventId, t.getMessage());
        jdbcTemplate.update(
                "UPDATE registrations SET status = 'CANCELLED', expires_at = NULL, updated_at = CURRENT_TIMESTAMP " +
                "WHERE activity_id IN (SELECT id FROM activities WHERE event_id = ?) " +
                "AND status IN ('CONFIRMED', 'PENDING_PAYMENT', 'WAITLIST')",
                eventId
        );
        jdbcTemplate.update("UPDATE activities SET occupied_slots = 0 WHERE event_id = ?", eventId);
    }
}
