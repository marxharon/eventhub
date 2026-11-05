package com.eventhub.registration.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "registrations")
public class Registration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "activity_id", nullable = false)
    private Long activityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RegistrationStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    protected Registration() {}

    public Registration(Long userId, Long activityId, RegistrationStatus status, LocalDateTime expiresAt) {
        this.userId = userId;
        this.activityId = activityId;
        this.status = status;
        this.expiresAt = expiresAt;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public void changeStatus(RegistrationStatus newStatus) {
        this.status = newStatus;
        this.expiresAt = null;
        this.updatedAt = LocalDateTime.now();
    }

    public void awaitPayment(LocalDateTime expiresAt) {
        this.status = RegistrationStatus.PENDING_PAYMENT;
        this.expiresAt = expiresAt;
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getActivityId() { return activityId; }
    public RegistrationStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
}
