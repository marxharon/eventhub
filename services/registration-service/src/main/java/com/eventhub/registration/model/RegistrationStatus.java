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

    /** Estados que ocupam uma vaga em occupied_slots (RN01). */
    public boolean holdsSlot() {
        return this == CONFIRMED || this == PENDING_PAYMENT;
    }
}
