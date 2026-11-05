package com.eventhub.event.dto;

import com.eventhub.event.model.Certificate;

import java.time.LocalDateTime;

public record CertificateResponse(
        Long id,
        String validationCode,
        Long userId,
        Long activityId,
        String activityTitle,
        LocalDateTime issuedAt,
        boolean valid
) {
    public static CertificateResponse fromEntity(Certificate certificate, String activityTitle) {
        return new CertificateResponse(
                certificate.getId(),
                certificate.getValidationCode(),
                certificate.getUserId(),
                certificate.getActivityId(),
                activityTitle,
                certificate.getIssuedAt(),
                true
        );
    }
}
