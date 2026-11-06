package com.eventhub.registration.model;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

/** Mapeamento somente leitura da tabela events (dono: event-service), usado para checar o organizador (RN07). */
@Entity
@Immutable
@Table(name = "events")
public class EventRef {

    @Id
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "organizer_id", nullable = false)
    private Long organizerId;

    @Column(nullable = false, length = 30)
    private String status;

    protected EventRef() {}

    public Long getId() { return id; }

    public String getTitle() { return title; }

    public Long getOrganizerId() { return organizerId; }

    public String getStatus() { return status; }
}
