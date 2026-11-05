package com.eventhub.event.dto;

import java.time.LocalDateTime;

/** Inscrição confirmada de um evento, com situação de presença, para o credenciamento (RF09). */
public record EventRegistrationResponse(
        Long registrationId,
        Long userId,
        String userName,
        String userEmail,
        Long activityId,
        String activityTitle,
        boolean activityCompleted,
        boolean checkedIn,
        LocalDateTime checkedInAt
) {
}
