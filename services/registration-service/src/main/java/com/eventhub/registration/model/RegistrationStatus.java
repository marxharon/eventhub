package com.eventhub.registration.model;

import java.util.List;

public enum RegistrationStatus {
    CONFIRMED,
    PENDING_PAYMENT,
    WAITLIST,
    CANCELLED,
    REFUSED,
    EXPIRED;

    public static final List<RegistrationStatus> ACTIVE = List.of(CONFIRMED, PENDING_PAYMENT, WAITLIST);

    public boolean isActive() {
        return ACTIVE.contains(this);
    }

    /** Estados que consomem capacidade: CONFIRMED (em occupied_slots) ou PENDING_PAYMENT (reserva). */
    public boolean holdsSlot() {
        return this == CONFIRMED || this == PENDING_PAYMENT;
    }
}
