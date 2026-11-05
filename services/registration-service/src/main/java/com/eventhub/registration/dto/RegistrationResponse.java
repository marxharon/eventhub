package com.eventhub.registration.dto;

import com.eventhub.registration.model.Registration;
import com.eventhub.registration.model.RegistrationStatus;

import java.time.LocalDateTime;

public record RegistrationResponse(
        Long id,
        Long userId,
        Long activityId,
        RegistrationStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime expiresAt
) {
    public static RegistrationResponse from(Registration r) {
        return new RegistrationResponse(
                r.getId(), r.getUserId(), r.getActivityId(), r.getStatus(),
                r.getCreatedAt(), r.getUpdatedAt(), r.getExpiresAt()
        );
    }
}
