package com.eventhub.event.dto;

import jakarta.validation.constraints.NotNull;

public record CertificateIssueRequest(
        @NotNull(message = "activityId é obrigatório.")
        Long activityId
) {}
