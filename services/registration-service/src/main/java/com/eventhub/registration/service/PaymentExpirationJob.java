package com.eventhub.registration.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PaymentExpirationJob {

    private final RegistrationService registrationService;

    public PaymentExpirationJob(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @Scheduled(fixedDelayString = "${eventhub.registration.expiration-check-ms:60000}")
    public void expirePendingPayments() {
        registrationService.expireOverdueReservations();
    }
}
