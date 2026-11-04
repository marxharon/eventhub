package com.eventhub.event.controller;

import com.eventhub.event.dto.ActivityRequest;
import com.eventhub.event.dto.ActivityResponse;
import com.eventhub.event.service.ActivityService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/events")
public class ActivityController {

    private final ActivityService activityService;

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    @GetMapping("/{eventId}/activities")
    public ResponseEntity<List<ActivityResponse>> listActivities(@PathVariable Long eventId) {
        return ResponseEntity.ok(activityService.listByEvent(eventId));
    }

    @PostMapping("/{eventId}/activities")
    public ResponseEntity<ActivityResponse> createActivity(
            @PathVariable Long eventId,
            @Valid @RequestBody ActivityRequest activityRequest,
            HttpServletRequest request
    ) {
        Long userId = (Long) request.getAttribute("authenticatedUserId");
        String role = (String) request.getAttribute("authenticatedRole");
        ActivityResponse created = activityService.createActivity(eventId, activityRequest, userId, role);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/activities/{activityId}")
    public ResponseEntity<ActivityResponse> updateActivity(
            @PathVariable Long activityId,
            @Valid @RequestBody ActivityRequest activityRequest,
            HttpServletRequest request
    ) {
        Long userId = (Long) request.getAttribute("authenticatedUserId");
        String role = (String) request.getAttribute("authenticatedRole");
        return ResponseEntity.ok(activityService.updateActivity(activityId, activityRequest, userId, role));
    }

    @PatchMapping("/activities/{activityId}/complete")
    public ResponseEntity<ActivityResponse> completeActivity(
            @PathVariable Long activityId,
            HttpServletRequest request
    ) {
        Long userId = (Long) request.getAttribute("authenticatedUserId");
        String role = (String) request.getAttribute("authenticatedRole");
        return ResponseEntity.ok(activityService.completeActivity(activityId, userId, role));
    }
}
