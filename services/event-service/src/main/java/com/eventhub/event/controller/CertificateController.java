package com.eventhub.event.controller;

import com.eventhub.event.dto.CertificateIssueRequest;
import com.eventhub.event.dto.CertificateResponse;
import com.eventhub.event.service.CertificateService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/events/certificates")
public class CertificateController {

    private final CertificateService certificateService;

    public CertificateController(CertificateService certificateService) {
        this.certificateService = certificateService;
    }

    @PostMapping("/issue")
    public ResponseEntity<CertificateResponse> issue(
            @Valid @RequestBody CertificateIssueRequest body,
            HttpServletRequest request
    ) {
        Long userId = currentUserId(request);
        String role = (String) request.getAttribute("authenticatedRole");
        CertificateResponse created = certificateService.issue(body.activityId(), userId, role);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/my")
    public List<CertificateResponse> listMine(HttpServletRequest request) {
        Long userId = currentUserId(request);
        String role = (String) request.getAttribute("authenticatedRole");
        return certificateService.listMine(userId, role);
    }

    @GetMapping("/validate/{validationCode}")
    public CertificateResponse validate(@PathVariable String validationCode) {
        return certificateService.validate(validationCode);
    }

    private Long currentUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("authenticatedUserId");
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token JWT ausente ou inválido.");
        }
        return (Long) userId;
    }
}
