package com.eventhub.event.service;

import com.eventhub.event.dto.AttendanceResponse;
import com.eventhub.event.model.Activity;
import com.eventhub.event.model.Attendance;
import com.eventhub.event.model.RegistrationRef;
import com.eventhub.event.repository.ActivityRepository;
import com.eventhub.event.repository.AttendanceRepository;
import com.eventhub.event.repository.RegistrationRefRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final RegistrationRefRepository registrationRepository;
    private final ActivityRepository activityRepository;
    private final EventService eventService;

    public AttendanceService(
            AttendanceRepository attendanceRepository,
            RegistrationRefRepository registrationRepository,
            ActivityRepository activityRepository,
            EventService eventService
    ) {
        this.attendanceRepository = attendanceRepository;
        this.registrationRepository = registrationRepository;
        this.activityRepository = activityRepository;
        this.eventService = eventService;
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
            return AttendanceResponse.fromEntity(saved);
        } catch (DataIntegrityViolationException ex) {
            // Proteção final contra concorrência, via restrição UNIQUE (registration_id)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "RF09: Check-in já realizado para esta inscrição.");
        }
    }
}
