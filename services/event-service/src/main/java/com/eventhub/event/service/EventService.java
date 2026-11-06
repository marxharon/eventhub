package com.eventhub.event.service;

import com.eventhub.event.dto.ActivityResponse;
import com.eventhub.event.dto.EventRequest;
import com.eventhub.event.dto.EventResponse;
import com.eventhub.event.model.Event;
import com.eventhub.event.model.EventStatus;
import com.eventhub.event.repository.ActivityRepository;
import com.eventhub.event.repository.EventRepository;
import com.eventhub.event.repository.RegistrationRefRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class EventService {

    private static final Logger log = LoggerFactory.getLogger(EventService.class);

    private final EventRepository eventRepository;
    private final ActivityRepository activityRepository;
    private final RegistrationRefRepository registrationRefRepository;
    private final NotificationClient notificationClient;
    private final JdbcTemplate jdbcTemplate;
    private final RestClient registrationClient;
    private final String registrationServiceUrl;

    public EventService(
            EventRepository eventRepository,
            ActivityRepository activityRepository,
            RegistrationRefRepository registrationRefRepository,
            NotificationClient notificationClient,
            JdbcTemplate jdbcTemplate,
            @Value("${eventhub.downstream.registration-service-url:http://registration-service:8084}") String registrationServiceUrl
    ) {
        this.eventRepository = eventRepository;
        this.activityRepository = activityRepository;
        this.registrationRefRepository = registrationRefRepository;
        this.notificationClient = notificationClient;
        this.jdbcTemplate = jdbcTemplate;
        this.registrationServiceUrl = registrationServiceUrl;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(2));
        this.registrationClient = RestClient.builder().requestFactory(factory).build();
    }

    @Transactional(readOnly = true)
    public List<EventResponse> listEvents(Long organizerIdFilter) {
        List<Event> events = (organizerIdFilter != null)
                ? eventRepository.findByOrganizerIdOrderByStartDateAsc(organizerIdFilter)
                : eventRepository.findAllByOrderByStartDateAsc();

        return events.stream()
                .map(this::toEventResponseWithActivities)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public EventResponse getEventById(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evento não encontrado."));
        return toEventResponseWithActivities(event);
    }

    @Transactional
    public EventResponse createEvent(EventRequest request, Long authenticatedUserId, String authenticatedRole) {
        ensureOrganizerRole(authenticatedRole);

        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A data de término não pode ser anterior à data de início.");
        }

        Event event = new Event();
        event.setTitle(request.getTitle().trim());
        event.setDescription(request.getDescription());
        event.setOrganization(request.getOrganization().trim());
        event.setOrganizerId(authenticatedUserId);
        event.setStartDate(request.getStartDate());
        event.setEndDate(request.getEndDate());
        event.setStatus(EventStatus.DRAFT);

        Event saved = eventRepository.save(event);
        return toEventResponseWithActivities(saved);
    }

    @Transactional
    public EventResponse updateEvent(Long eventId, EventRequest request, Long authenticatedUserId, String authenticatedRole) {
        ensureOrganizerRole(authenticatedRole);
        Event event = getOwnedEventOrThrow(eventId, authenticatedUserId);

        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A data de término não pode ser anterior à data de início.");
        }

        event.setTitle(request.getTitle().trim());
        event.setDescription(request.getDescription());
        event.setOrganization(request.getOrganization().trim());
        event.setStartDate(request.getStartDate());
        event.setEndDate(request.getEndDate());

        Event updated = eventRepository.save(event);
        registrationRefRepository.findActiveUserIdsByEventId(eventId).forEach(uid ->
                notificationClient.notifyUser(
                        uid,
                        "EVENT_UPDATED",
                        "O evento \"" + updated.getTitle() + "\" teve informações atualizadas pela organização."
                )
        );
        return toEventResponseWithActivities(updated);
    }

    @Transactional
    public EventResponse publishEvent(Long eventId, Long authenticatedUserId, String authenticatedRole) {
        ensureOrganizerRole(authenticatedRole);
        Event event = getOwnedEventOrThrow(eventId, authenticatedUserId);

        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Um evento cancelado não pode ser publicado.");
        }

        event.setStatus(EventStatus.PUBLISHED);
        Event saved = eventRepository.save(event);
        return toEventResponseWithActivities(saved);
    }

    @Transactional
    public EventResponse cancelEvent(Long eventId, Long authenticatedUserId, String authenticatedRole) {
        ensureOrganizerRole(authenticatedRole);
        Event event = getOwnedEventOrThrow(eventId, authenticatedUserId);

        List<Long> affectedUsers = registrationRefRepository.findActiveUserIdsByEventId(eventId);

        event.setStatus(EventStatus.CANCELLED);
        Event saved = eventRepository.save(event);

        try {
            registrationClient.post()
                    .uri(registrationServiceUrl + "/api/registrations/event/" + eventId + "/cancel-all")
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            log.warn("Fallback local ao cancelar inscricoes do evento {}: {}", eventId, ex.getMessage());
            jdbcTemplate.update(
                    "UPDATE registrations SET status = 'CANCELLED', expires_at = NULL, updated_at = CURRENT_TIMESTAMP " +
                    "WHERE activity_id IN (SELECT id FROM activities WHERE event_id = ?) " +
                    "AND status IN ('CONFIRMED', 'PENDING_PAYMENT', 'WAITLIST')",
                    eventId
            );
            jdbcTemplate.update("UPDATE activities SET occupied_slots = 0 WHERE event_id = ?", eventId);
        }

        affectedUsers.forEach(uid ->
                notificationClient.notifyUser(
                        uid,
                        "EVENT_CANCELLED",
                        "O evento \"" + saved.getTitle() + "\" foi cancelado pela organização."
                )
        );

        return toEventResponseWithActivities(saved);
    }

    public Event getOwnedEventOrThrow(Long eventId, Long authenticatedUserId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evento não encontrado."));

        // RN07: Organizadores administram apenas eventos sob sua responsabilidade
        if (!event.getOrganizerId().equals(authenticatedUserId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "RN07 Violada: Você só pode administrar eventos sob sua própria responsabilidade."
            );
        }
        return event;
    }

    public void ensureOrganizerRole(String authenticatedRole) {
        if (!"ORGANIZER".equals(authenticatedRole)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "RN07 Violada: Apenas usuários com perfil ORGANIZER podem gerenciar eventos e atividades."
            );
        }
    }

    private EventResponse toEventResponseWithActivities(Event event) {
        List<ActivityResponse> activities = activityRepository.findByEventIdOrderByStartTimeAsc(event.getId())
                .stream()
                .map(ActivityResponse::fromEntity)
                .collect(Collectors.toList());
        return EventResponse.fromEntity(event, activities);
    }
}
