package com.eventhub.registration.model;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

/** Mapeamento somente leitura da tabela attendances (dono: event-service), usado para o comparecimento (RF11). */
@Entity
@Immutable
@Table(name = "attendances")
public class AttendanceRef {

    @Id
    private Long id;

    @Column(name = "activity_id", nullable = false)
    private Long activityId;

    protected AttendanceRef() {}

    public Long getId() { return id; }

    public Long getActivityId() { return activityId; }
}
