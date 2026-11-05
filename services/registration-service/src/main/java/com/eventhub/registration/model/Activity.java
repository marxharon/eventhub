package com.eventhub.registration.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

/** Mapeamento parcial da tabela activities (dono: event-service). Usado para controle de vagas (RN01). */
@Entity
@Table(name = "activities")
public class Activity {

    @Id
    private Long id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false)
    private Integer capacity;

    @Column(name = "occupied_slots", nullable = false)
    private Integer occupiedSlots;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "waitlist_enabled", nullable = false)
    private Boolean waitlistEnabled;

    @Column(nullable = false)
    private Boolean completed;

    protected Activity() {}

    public Long getId() { return id; }

    public Long getEventId() { return eventId; }

    public String getTitle() { return title; }

    public Integer getCapacity() { return capacity; }

    public Integer getOccupiedSlots() { return occupiedSlots; }
    public void setOccupiedSlots(Integer occupiedSlots) { this.occupiedSlots = occupiedSlots; }

    public BigDecimal getPrice() { return price; }

    public Boolean getWaitlistEnabled() { return waitlistEnabled; }

    public Boolean getCompleted() { return completed; }

    public boolean isFree() { return price.signum() == 0; }
}
