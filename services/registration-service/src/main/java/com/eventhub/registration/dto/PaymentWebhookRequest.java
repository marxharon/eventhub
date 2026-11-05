package com.eventhub.registration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PaymentWebhookRequest(
        @NotNull(message = "registrationId é obrigatório") Long registrationId,
        Long paymentId,
        @NotBlank(message = "paymentStatus é obrigatório") String paymentStatus
) {}
