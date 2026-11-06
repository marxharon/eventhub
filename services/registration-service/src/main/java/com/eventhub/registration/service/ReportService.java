package com.eventhub.registration.service;

import com.eventhub.registration.dto.OrganizerReportDTO;
import com.eventhub.registration.dto.OrganizerReportDTO.ActivityReport;
import com.eventhub.registration.dto.OrganizerReportDTO.Totals;
import com.eventhub.registration.model.Activity;
import com.eventhub.registration.model.EventRef;
import com.eventhub.registration.model.RegistrationStatus;
import com.eventhub.registration.repository.ActivityRepository;
import com.eventhub.registration.repository.AttendanceRefRepository;
import com.eventhub.registration.repository.EventRefRepository;
import com.eventhub.registration.repository.RegistrationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReportService {

    private final EventRefRepository eventRepository;
    private final ActivityRepository activityRepository;
    private final RegistrationRepository registrationRepository;
    private final AttendanceRefRepository attendanceRepository;

    public ReportService(
            EventRefRepository eventRepository,
            ActivityRepository activityRepository,
            RegistrationRepository registrationRepository,
            AttendanceRefRepository attendanceRepository
    ) {
        this.eventRepository = eventRepository;
        this.activityRepository = activityRepository;
        this.registrationRepository = registrationRepository;
        this.attendanceRepository = attendanceRepository;
    }

    /**
     * Relatório gerencial do evento (RF11). Apenas o organizador dono do evento (RN07).
     * Receita = preço da atividade x inscrições CONFIRMED.
     */
    @Transactional(readOnly = true)
    public OrganizerReportDTO eventReport(Long eventId, Long authenticatedUserId, String authenticatedRole) {
        if (!"ORGANIZER".equals(authenticatedRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "RN07 Violada: Apenas organizadores acessam relatórios.");
        }

        EventRef event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evento não encontrado."));

        if (!event.getOrganizerId().equals(authenticatedUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "RN07 Violada: O evento pertence a outro organizador.");
        }

        List<Activity> activities = activityRepository.findByEventIdOrderByIdAsc(eventId);
        if (activities.isEmpty()) {
            return new OrganizerReportDTO(eventId, event.getTitle(), emptyTotals(), List.of());
        }

        List<Long> activityIds = activities.stream().map(Activity::getId).toList();

        Map<Long, EnumMap<RegistrationStatus, Long>> countsByActivity = new HashMap<>();
        for (Object[] row : registrationRepository.countByActivityAndStatus(activityIds)) {
            Long activityId = (Long) row[0];
            RegistrationStatus status = (RegistrationStatus) row[1];
            long count = ((Number) row[2]).longValue();
            countsByActivity.computeIfAbsent(activityId, k -> new EnumMap<>(RegistrationStatus.class)).put(status, count);
        }

        Map<Long, Long> attendanceByActivity = new HashMap<>();
        for (Object[] row : attendanceRepository.countByActivity(activityIds)) {
            attendanceByActivity.put((Long) row[0], ((Number) row[1]).longValue());
        }

        List<ActivityReport> activityReports = activities.stream()
                .map(a -> buildActivityReport(a, countsByActivity, attendanceByActivity))
                .toList();

        Totals totals = sumTotals(activityReports);
        return new OrganizerReportDTO(eventId, event.getTitle(), totals, activityReports);
    }

    private ActivityReport buildActivityReport(
            Activity activity,
            Map<Long, EnumMap<RegistrationStatus, Long>> countsByActivity,
            Map<Long, Long> attendanceByActivity
    ) {
        EnumMap<RegistrationStatus, Long> counts = countsByActivity.getOrDefault(
                activity.getId(), new EnumMap<>(RegistrationStatus.class));

        long confirmed = counts.getOrDefault(RegistrationStatus.CONFIRMED, 0L);
        long pending = counts.getOrDefault(RegistrationStatus.PENDING_PAYMENT, 0L);
        long waitlist = counts.getOrDefault(RegistrationStatus.WAITLIST, 0L);
        long cancelled = counts.getOrDefault(RegistrationStatus.CANCELLED, 0L);
        long refused = counts.getOrDefault(RegistrationStatus.REFUSED, 0L);
        long expired = counts.getOrDefault(RegistrationStatus.EXPIRED, 0L);
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        long attendances = attendanceByActivity.getOrDefault(activity.getId(), 0L);
        BigDecimal revenue = activity.getPrice().multiply(BigDecimal.valueOf(confirmed));

        return new ActivityReport(
                activity.getId(),
                activity.getTitle(),
                activity.getCapacity(),
                total,
                confirmed,
                pending,
                waitlist,
                cancelled,
                refused,
                expired,
                attendances,
                revenue
        );
    }

    private Totals sumTotals(List<ActivityReport> reports) {
        long registrations = 0, confirmed = 0, pending = 0, waitlist = 0, cancelled = 0, refused = 0, expired = 0, attendances = 0;
        BigDecimal revenue = BigDecimal.ZERO;
        for (ActivityReport r : reports) {
            registrations += r.registrations();
            confirmed += r.confirmed();
            pending += r.pendingPayment();
            waitlist += r.waitlist();
            cancelled += r.cancelled();
            refused += r.refused();
            expired += r.expired();
            attendances += r.attendances();
            revenue = revenue.add(r.revenue());
        }
        return new Totals(registrations, confirmed, pending, waitlist, cancelled, refused, expired, attendances, revenue);
    }

    private Totals emptyTotals() {
        return new Totals(0, 0, 0, 0, 0, 0, 0, 0, BigDecimal.ZERO);
    }
}
