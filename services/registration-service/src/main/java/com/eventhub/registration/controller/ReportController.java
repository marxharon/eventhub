package com.eventhub.registration.controller;

import com.eventhub.registration.dto.OrganizerReportDTO;
import com.eventhub.registration.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Tag(name = "Relatórios", description = "Relatórios gerenciais do organizador (RF11, RN07)")
@RestController
@RequestMapping("/api/registrations/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @Operation(summary = "Relatório gerencial do evento", description = "Restrito ao ORGANIZER dono do evento: totais e indicadores por atividade.")
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
