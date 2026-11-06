package com.eventhub.event.controller;

import com.eventhub.event.dto.EventRequest;
import com.eventhub.event.dto.EventResponse;
import com.eventhub.event.service.EventService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "event-service",
                "stack", "Java 21 / Spring Boot 3",
                "port", 8082,
                "requirements", List.of("RF02", "RF03", "RF09", "RF10", "RN06", "RN07")
        ));
    }

    @GetMapping
    public ResponseEntity<List<EventResponse>> listEvents(
            @RequestParam(required = false) Long organizerId
    ) {
        return ResponseEntity.ok(eventService.listEvents(organizerId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EventResponse> getEventById(@PathVariable Long id) {
        return ResponseEntity.ok(eventService.getEventById(id));
    }

    @PostMapping
    public ResponseEntity<EventResponse> createEvent(
            @Valid @RequestBody EventRequest eventRequest,
            HttpServletRequest request
    ) {
        Long userId = (Long) request.getAttribute("authenticatedUserId");
        String role = (String) request.getAttribute("authenticatedRole");
        EventResponse created = eventService.createEvent(eventRequest, userId, role);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<EventResponse> updateEvent(
            @PathVariable Long id,
            @Valid @RequestBody EventRequest eventRequest,
            HttpServletRequest request
    ) {
        Long userId = (Long) request.getAttribute("authenticatedUserId");
        String role = (String) request.getAttribute("authenticatedRole");
        return ResponseEntity.ok(eventService.updateEvent(id, eventRequest, userId, role));
    }

    @PatchMapping("/{id}/publish")
    public ResponseEntity<EventResponse> publishEvent(
            @PathVariable Long id,
            HttpServletRequest request
    ) {
        Long userId = (Long) request.getAttribute("authenticatedUserId");
        String role = (String) request.getAttribute("authenticatedRole");
        return ResponseEntity.ok(eventService.publishEvent(id, userId, role));
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<EventResponse> cancelEvent(
            @PathVariable Long id,
            HttpServletRequest request
    ) {
        Long userId = (Long) request.getAttribute("authenticatedUserId");
        String role = (String) request.getAttribute("authenticatedRole");
        String authHeader = request.getHeader("Authorization");
        return ResponseEntity.ok(eventService.cancelEvent(id, userId, role, authHeader));
    }
}
