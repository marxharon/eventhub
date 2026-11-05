package com.eventhub.event.service;

import com.eventhub.event.dto.AttendanceResponse;
import com.eventhub.event.dto.EventRegistrationResponse;
import com.eventhub.event.model.Activity;
import com.eventhub.event.model.Attendance;
import com.eventhub.event.model.RegistrationRef;
import com.eventhub.event.repository.ActivityRepository;
import com.eventhub.event.repository.AttendanceRepository;
import com.eventhub.event.repository.RegistrationRefRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.util.List;

@Service
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final RegistrationRefRepository registrationRepository;
    private final ActivityRepository activityRepository;
    private final EventService eventService;
    private final CertificateService certificateService;
    private final JdbcTemplate jdbcTemplate;

    public AttendanceService(
            AttendanceRepository attendanceRepository,
            RegistrationRefRepository registrationRepository,
            ActivityRepository activityRepository,
            EventService eventService,
            CertificateService certificateService,
            JdbcTemplate jdbcTemplate
    ) {
        this.attendanceRepository = attendanceRepository;
        this.registrationRepository = registrationRepository;
        this.activityRepository = activityRepository;
        this.eventService = eventService;
        this.certificateService = certificateService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Inscrições confirmadas de um evento sob responsabilidade do organizador (RN07), com situação de presença. */
    @Transactional(readOnly = true)
    public List<EventRegistrationResponse> listEventRegistrations(Long eventId, Long authenticatedUserId, String authenticatedRole) {
        eventService.ensureOrganizerRole(authenticatedRole);
        eventService.getOwnedEventOrThrow(eventId, authenticatedUserId);

        return jdbcTemplate.query(
                """
                SELECT r.id AS registration_id, r.user_id, u.name AS user_name, u.email AS user_email,
                       a.id AS activity_id, a.title AS activity_title, a.completed AS activity_completed,
                       att.id IS NOT NULL AS checked_in, att.checked_in_at AS checked_in_at
                FROM registrations r
                JOIN activities a ON a.id = r.activity_id
                JOIN users u ON u.id = r.user_id
                LEFT JOIN attendances att ON att.registration_id = r.id
                WHERE a.event_id = ? AND r.status = 'CONFIRMED'
                ORDER BY a.start_time, u.name
                """,
                (rs, rowNum) -> {
                    Timestamp checkedInAt = rs.getTimestamp("checked_in_at");
                    return new EventRegistrationResponse(
                            rs.getLong("registration_id"),
                            rs.getLong("user_id"),
                            rs.getString("user_name"),
                            rs.getString("user_email"),
                            rs.getLong("activity_id"),
                            rs.getString("activity_title"),
                            rs.getBoolean("activity_completed"),
                            rs.getBoolean("checked_in"),
                            checkedInAt != null ? checkedInAt.toLocalDateTime() : null
                    );
                },
                eventId
        );
    }

    /** Check-in de presença (RF09). Apenas o organizador responsável pelo evento (RN07). */
    @Transactional
    public AttendanceResponse checkIn(Long registrationId, Long authenticatedUserId, String authenticatedRole) {
        eventService.ensureOrganizerRole(authenticatedRole);

        RegistrationRef registration = registrationRepository.findById(registrationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inscrição não encontrada."));

        Activity activity = activityRepository.findById(registration.getActivityId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Atividade não encontrada."));

        eventService.getOwnedEventOrThrow(activity.getEventId(), authenticatedUserId);

        if (!"CONFIRMED".equals(registration.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "RF09: Somente inscrições CONFIRMED podem registrar presença. Status atual: " + registration.getStatus() + "."
            );
        }

        if (attendanceRepository.existsByRegistrationId(registrationId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "RF09: Check-in já realizado para esta inscrição.");
        }

        try {
            Attendance saved = attendanceRepository.saveAndFlush(
                    new Attendance(registration.getId(), registration.getUserId(), registration.getActivityId())
            );
            // Se a atividade já estiver concluída, o certificado fica disponível imediatamente (RN06)
            certificateService.issueForAttendance(saved, activity);
            return AttendanceResponse.fromEntity(saved);
        } catch (DataIntegrityViolationException ex) {
            // Proteção final contra concorrência, via restrição UNIQUE (registration_id)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "RF09: Check-in já realizado para esta inscrição.");
        }
    }
}
