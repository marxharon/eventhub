package com.eventhub.registration.dto;

import java.math.BigDecimal;
import java.util.List;

/** Relatório gerencial do evento para o organizador (RF11). */
public record OrganizerReportDTO(
        Long eventId,
        String eventTitle,
        Totals totals,
        List<ActivityReport> activities
) {

    public record Totals(
            long registrations,
            long confirmed,
            long pendingPayment,
            long waitlist,
            long cancelled,
            long refused,
            long expired,
            long attendances,
            BigDecimal revenue
    ) {}

    public record ActivityReport(
            Long activityId,
            String title,
            int capacity,
            long registrations,
            long confirmed,
            long pendingPayment,
            long waitlist,
            long cancelled,
            long refused,
            long expired,
            long attendances,
            BigDecimal revenue
    ) {}
}
