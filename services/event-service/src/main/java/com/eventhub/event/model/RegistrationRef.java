package com.eventhub.event.model;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

/**
 * Mapeamento somente leitura da tabela registrations (dono: registration-service).
 * Usado para validar a inscrição no check-in (RF09) sem acoplamento de escrita.
 */
@Entity
@Immutable
@Table(name = "registrations")
public class RegistrationRef {

    @Id
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "activity_id", nullable = false)
    private Long activityId;

    @Column(nullable = false, length = 30)
    private String status;

    protected RegistrationRef() {}

    public Long getId() { return id; }

    public Long getUserId() { return userId; }

    public Long getActivityId() { return activityId; }

    public String getStatus() { return status; }
}
