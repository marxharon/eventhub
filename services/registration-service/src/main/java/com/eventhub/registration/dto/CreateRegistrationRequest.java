package com.eventhub.registration.dto;

import jakarta.validation.constraints.NotNull;

public record CreateRegistrationRequest(
        @NotNull(message = "activityId é obrigatório") Long activityId
) {}
