package com.eventhub.registration.controller;

import com.eventhub.registration.dto.CreateRegistrationRequest;
import com.eventhub.registration.dto.PaymentWebhookRequest;
import com.eventhub.registration.dto.RegistrationResponse;
import com.eventhub.registration.service.RegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Tag(name = "Inscrições", description = "Inscrições, cancelamentos, lista de espera e webhook de pagamento (RF05, RF06, RN01-RN04)")
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

    @Operation(summary = "Lista as inscrições do usuário autenticado")
    @GetMapping("/my")
    public List<RegistrationResponse> listMine(HttpServletRequest request) {
        return registrationService.listMine(currentUserId(request));
    }

    @GetMapping("/{registrationId}")
    public RegistrationResponse get(@PathVariable Long registrationId, HttpServletRequest request) {
        return registrationService.getOwned(currentUserId(request), registrationId);
    }

    @Operation(summary = "Cancela uma inscrição do usuário autenticado", description = "Libera a vaga e promove o próximo da lista de espera (RN04).")
    @PatchMapping("/{registrationId}/cancel")
    public RegistrationResponse cancel(@PathVariable Long registrationId, HttpServletRequest request) {
        return registrationService.cancel(currentUserId(request), registrationId);
    }

    @Operation(summary = "Recebe o resultado de pagamento (webhook do payment-service)", description = "Confirma a inscrição ou libera a vaga conforme o resultado do pagamento (RN02, RN03).")
    @PostMapping("/payment-webhook")
    public RegistrationResponse paymentWebhook(@Valid @RequestBody PaymentWebhookRequest body) {
        return registrationService.applyPaymentResult(body);
    }

    @Operation(summary = "Cancela todas as inscrições ativas de um evento (RF02, RF05)")
    @PostMapping("/event/{eventId}/cancel-all")
    public Map<String, Object> cancelAllByEvent(@PathVariable Long eventId, HttpServletRequest request) {
        Long userId = currentUserId(request);
        String role = (String) request.getAttribute("authenticatedRole");
        int cancelledCount = registrationService.cancelAllByEvent(eventId, userId, role);
        return Map.of("eventId", eventId, "cancelledRegistrations", cancelledCount);
    }

    private Long currentUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("authenticatedUserId");
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token JWT ausente ou inválido.");
        }
        return (Long) userId;
    }
}
