package com.eventhub.event.controller;

import com.eventhub.event.dto.AttendanceCheckInRequest;
import com.eventhub.event.dto.AttendanceResponse;
import com.eventhub.event.service.AttendanceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/events/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @PostMapping("/check-in")
    public ResponseEntity<AttendanceResponse> checkIn(
            @Valid @RequestBody AttendanceCheckInRequest body,
            HttpServletRequest request
    ) {
        Long userId = (Long) request.getAttribute("authenticatedUserId");
        String role = (String) request.getAttribute("authenticatedRole");
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token JWT ausente ou inválido.");
        }
        AttendanceResponse created = attendanceService.checkIn(body.registrationId(), userId, role);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
