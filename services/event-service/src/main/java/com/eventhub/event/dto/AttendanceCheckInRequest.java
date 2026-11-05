package com.eventhub.event.dto;

import jakarta.validation.constraints.NotNull;

public record AttendanceCheckInRequest(
        @NotNull(message = "registrationId é obrigatório.")
        Long registrationId
) {}
