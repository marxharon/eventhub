package com.eventhub.event.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Certificado emitido para participante elegível (RF10, RN06). Dono: event-service. */
@Entity
@Table(name = "certificates")
public class Certificate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "validation_code", nullable = false, unique = true, length = 64)
    private String validationCode;

    @Column(name = "registration_id", nullable = false, unique = true)
    private Long registrationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "activity_id", nullable = false)
    private Long activityId;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt = LocalDateTime.now();

    protected Certificate() {}

    public Certificate(String validationCode, Long registrationId, Long userId, Long activityId) {
        this.validationCode = validationCode;
        this.registrationId = registrationId;
        this.userId = userId;
        this.activityId = activityId;
        this.issuedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }

    public String getValidationCode() { return validationCode; }

    public Long getRegistrationId() { return registrationId; }

    public Long getUserId() { return userId; }

    public Long getActivityId() { return activityId; }

    public LocalDateTime getIssuedAt() { return issuedAt; }
}
