package com.eventhub.registration.controller;

import com.eventhub.registration.dto.CreateRegistrationRequest;
import com.eventhub.registration.dto.PaymentWebhookRequest;
import com.eventhub.registration.dto.RegistrationResponse;
import com.eventhub.registration.service.RegistrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/registrations")
public class RegistrationController {

    private final RegistrationService registrationService;

    public RegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", "registration-service",
                "stack", "Java 21 / Spring Boot 3.3",
                "port", 8084,
                "requirements", List.of("RF05", "RF06", "RF09", "RN01", "RN02", "RN03", "RN04", "RN05", "RN07", "RQ04")
        );
    }

    @PostMapping
    public ResponseEntity<RegistrationResponse> create(
            @Valid @RequestBody CreateRegistrationRequest body,
            HttpServletRequest request
    ) {
        RegistrationResponse response = registrationService.create(currentUserId(request), body.activityId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/my")
    public List<RegistrationResponse> listMine(HttpServletRequest request) {
        return registrationService.listMine(currentUserId(request));
    }

    @GetMapping("/{registrationId}")
    public RegistrationResponse get(@PathVariable Long registrationId, HttpServletRequest request) {
        return registrationService.getOwned(currentUserId(request), registrationId);
    }

    @PatchMapping("/{registrationId}/cancel")
    public RegistrationResponse cancel(@PathVariable Long registrationId, HttpServletRequest request) {
        return registrationService.cancel(currentUserId(request), registrationId);
    }

    @PostMapping("/payment-webhook")
    public RegistrationResponse paymentWebhook(@Valid @RequestBody PaymentWebhookRequest body) {
        return registrationService.applyPaymentResult(body);
    }

    private Long currentUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("authenticatedUserId");
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token JWT ausente ou inválido.");
        }
        return (Long) userId;
    }
}
