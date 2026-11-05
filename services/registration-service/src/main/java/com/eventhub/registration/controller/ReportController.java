package com.eventhub.registration.controller;

import com.eventhub.registration.dto.OrganizerReportDTO;
import com.eventhub.registration.service.ReportService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/registrations/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/event/{eventId}")
    public OrganizerReportDTO eventReport(@PathVariable Long eventId, HttpServletRequest request) {
        Object userId = request.getAttribute("authenticatedUserId");
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token JWT ausente ou inválido.");
        }
        String role = (String) request.getAttribute("authenticatedRole");
        return reportService.eventReport(eventId, (Long) userId, role);
    }
}
