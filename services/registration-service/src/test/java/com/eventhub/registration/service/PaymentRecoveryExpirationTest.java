package com.eventhub.registration.service;

import com.eventhub.registration.model.Activity;
import com.eventhub.registration.model.Registration;
import com.eventhub.registration.model.RegistrationStatus;
import com.eventhub.registration.repository.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentRecoveryExpirationTest {
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void approvedPaymentProtectsOverdueReservationWhileCallbackIsPending(boolean approved) {
        ActivityRepository activities = mock(ActivityRepository.class);
        RegistrationRepository registrations = mock(RegistrationRepository.class);
        ExternalIntegrationService external = mock(ExternalIntegrationService.class);
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        doAnswer(invocation -> {
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactions).executeWithoutResult(any());
        Registration reg = new Registration(2L, 10L, RegistrationStatus.PENDING_PAYMENT,
                LocalDateTime.now().minusMinutes(1));
        Activity activity = mock(Activity.class);
        when(activity.getId()).thenReturn(10L);
        when(registrations.findExpiredIds(eq(RegistrationStatus.PENDING_PAYMENT), any())).thenReturn(List.of(1L));
        when(registrations.findActivityIdById(1L)).thenReturn(Optional.of(10L));
        when(activities.findByIdForUpdate(10L)).thenReturn(Optional.of(activity));
        when(registrations.findByIdForUpdate(1L)).thenReturn(Optional.of(reg));
        when(registrations.hasApprovedPayment(1L)).thenReturn(approved);
        when(registrations.findFirstByActivityIdAndStatusOrderByCreatedAtAscIdAsc(10L, RegistrationStatus.WAITLIST))
                .thenReturn(Optional.empty());
        RegistrationService service = new RegistrationService(activities, mock(EventRefRepository.class),
                registrations, external, transactions, 15);
        service.expireOverdueReservations();
        assertEquals(approved ? RegistrationStatus.PENDING_PAYMENT : RegistrationStatus.EXPIRED, reg.getStatus());
        verify(activity, never()).setOccupiedSlots(anyInt());
    }
}
