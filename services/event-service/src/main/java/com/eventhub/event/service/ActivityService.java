package com.eventhub.event.service;

import com.eventhub.event.dto.ActivityRequest;
import com.eventhub.event.dto.ActivityResponse;
import com.eventhub.event.model.Activity;
import com.eventhub.event.model.Event;
import com.eventhub.event.model.EventStatus;
import com.eventhub.event.repository.ActivityRepository;
import com.eventhub.event.repository.AttendanceRepository;
import com.eventhub.event.repository.RegistrationRefRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ActivityService {

    private final ActivityRepository activityRepository;
    private final AttendanceRepository attendanceRepository;
    private final RegistrationRefRepository registrationRefRepository;
    private final NotificationClient notificationClient;
    private final EventService eventService;
    private final CertificateService certificateService;

    public ActivityService(
            ActivityRepository activityRepository,
            AttendanceRepository attendanceRepository,
            RegistrationRefRepository registrationRefRepository,
            NotificationClient notificationClient,
            EventService eventService,
            CertificateService certificateService
    ) {
        this.activityRepository = activityRepository;
        this.attendanceRepository = attendanceRepository;
        this.registrationRefRepository = registrationRefRepository;
        this.notificationClient = notificationClient;
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
        Activity activity = activityRepository.findByIdForUpdate(activityId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Atividade não encontrada."));

        eventService.getOwnedEventOrThrow(activity.getEventId(), authenticatedUserId);

        if (request.getEndTime().isBefore(request.getStartTime())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O horário de término da atividade não pode ser anterior ao início.");
        }

        long pendingPaymentSlots = registrationRefRepository.countPendingPaymentByActivityId(activityId);
        long totalReservedSlots = activity.getOccupiedSlots() + pendingPaymentSlots;

        if (request.getCapacity() < totalReservedSlots) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "RN01: A nova capacidade não pode ser menor que o total de vagas já reservadas (" + totalReservedSlots
                            + ": " + activity.getOccupiedSlots() + " confirmadas e " + pendingPaymentSlots + " aguardando pagamento)."
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
        registrationRefRepository.findActiveUserIdsByActivityId(activityId).forEach(uid ->
                notificationClient.notifyUser(
                        uid,
                        "ACTIVITY_UPDATED",
                        "A atividade \"" + saved.getTitle() + "\" teve informações (horário/local) atualizadas."
                )
        );
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

        Event event = eventService.getOwnedEventOrThrow(activity.getEventId(), authenticatedUserId);
        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "RN06: Somente atividades de eventos publicados podem ser concluídas."
            );
        }
        if (activity.getEndTime() == null || LocalDateTime.now().isBefore(activity.getEndTime())) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "RN06: A atividade só pode ser concluída após seu horário de término (" + activity.getEndTime() + ")."
            );
        }

        activity.setCompleted(true);
        Activity saved = activityRepository.save(activity);

        // Concluída a atividade, os participantes com presença recebem o certificado (RN06) e a notificação
        attendanceRepository.findByActivityId(activityId)
                .forEach(attendance -> certificateService.issueForAttendance(attendance, saved));

        return ActivityResponse.fromEntity(saved);
    }
}
