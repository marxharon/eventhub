package com.eventhub.event.service;

import com.eventhub.event.dto.ActivityRequest;
import com.eventhub.event.dto.ActivityResponse;
import com.eventhub.event.model.Activity;
import com.eventhub.event.model.Event;
import com.eventhub.event.repository.ActivityRepository;
import com.eventhub.event.repository.AttendanceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ActivityService {

    private final ActivityRepository activityRepository;
    private final AttendanceRepository attendanceRepository;
    private final EventService eventService;
    private final CertificateService certificateService;

    public ActivityService(
            ActivityRepository activityRepository,
            AttendanceRepository attendanceRepository,
            EventService eventService,
            CertificateService certificateService
    ) {
        this.activityRepository = activityRepository;
        this.attendanceRepository = attendanceRepository;
        this.eventService = eventService;
        this.certificateService = certificateService;
    }

    @Transactional(readOnly = true)
    public List<ActivityResponse> listByEvent(Long eventId) {
        return activityRepository.findByEventIdOrderByStartTimeAsc(eventId)
                .stream()
                .map(ActivityResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public ActivityResponse createActivity(
            Long eventId,
            ActivityRequest request,
            Long authenticatedUserId,
            String authenticatedRole
    ) {
        eventService.ensureOrganizerRole(authenticatedRole);
        Event event = eventService.getOwnedEventOrThrow(eventId, authenticatedUserId);

        if (request.getEndTime().isBefore(request.getStartTime())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O horário de término da atividade não pode ser anterior ao início.");
        }

        Activity activity = new Activity();
        activity.setEventId(event.getId());
        activity.setTitle(request.getTitle().trim());
        activity.setDescription(request.getDescription());
        activity.setSpeaker(request.getSpeaker().trim());
        activity.setLocation(request.getLocation().trim());
        activity.setStartTime(request.getStartTime());
        activity.setEndTime(request.getEndTime());
        activity.setCapacity(request.getCapacity());
        activity.setOccupiedSlots(0);
        activity.setPrice(request.getPrice() != null ? request.getPrice() : BigDecimal.ZERO);
        activity.setWaitlistEnabled(request.getWaitlistEnabled() != null ? request.getWaitlistEnabled() : true);
        activity.setCompleted(false);

        Activity saved = activityRepository.save(activity);
        return ActivityResponse.fromEntity(saved);
    }

    @Transactional
    public ActivityResponse updateActivity(
            Long activityId,
            ActivityRequest request,
            Long authenticatedUserId,
            String authenticatedRole
    ) {
        eventService.ensureOrganizerRole(authenticatedRole);
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Atividade não encontrada."));

        eventService.getOwnedEventOrThrow(activity.getEventId(), authenticatedUserId);

        if (request.getCapacity() < activity.getOccupiedSlots()) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "RN01: A nova capacidade não pode ser menor que o número de vagas já ocupadas (" + activity.getOccupiedSlots() + ")."
            );
        }

        activity.setTitle(request.getTitle().trim());
        activity.setDescription(request.getDescription());
        activity.setSpeaker(request.getSpeaker().trim());
        activity.setLocation(request.getLocation().trim());
        activity.setStartTime(request.getStartTime());
        activity.setEndTime(request.getEndTime());
        activity.setCapacity(request.getCapacity());
        activity.setPrice(request.getPrice() != null ? request.getPrice() : BigDecimal.ZERO);
        activity.setWaitlistEnabled(request.getWaitlistEnabled() != null ? request.getWaitlistEnabled() : true);

        Activity saved = activityRepository.save(activity);
        return ActivityResponse.fromEntity(saved);
    }

    @Transactional
    public ActivityResponse completeActivity(
            Long activityId,
            Long authenticatedUserId,
            String authenticatedRole
    ) {
        eventService.ensureOrganizerRole(authenticatedRole);
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Atividade não encontrada."));

        eventService.getOwnedEventOrThrow(activity.getEventId(), authenticatedUserId);
        activity.setCompleted(true);
        Activity saved = activityRepository.save(activity);

        // Concluída a atividade, os participantes com presença recebem o certificado (RN06) e a notificação
        attendanceRepository.findByActivityId(activityId)
                .forEach(attendance -> certificateService.issueForAttendance(attendance, saved));

        return ActivityResponse.fromEntity(saved);
    }
}
