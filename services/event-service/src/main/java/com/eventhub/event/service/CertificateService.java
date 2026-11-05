package com.eventhub.event.service;

import com.eventhub.event.dto.CertificateResponse;
import com.eventhub.event.model.Activity;
import com.eventhub.event.model.Attendance;
import com.eventhub.event.model.Certificate;
import com.eventhub.event.repository.ActivityRepository;
import com.eventhub.event.repository.AttendanceRepository;
import com.eventhub.event.repository.CertificateRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class CertificateService {

    private final CertificateRepository certificateRepository;
    private final AttendanceRepository attendanceRepository;
    private final ActivityRepository activityRepository;

    public CertificateService(
            CertificateRepository certificateRepository,
            AttendanceRepository attendanceRepository,
            ActivityRepository activityRepository
    ) {
        this.certificateRepository = certificateRepository;
        this.attendanceRepository = attendanceRepository;
        this.activityRepository = activityRepository;
    }

    /** Emissão de certificado (RF10). Regras RN06: atividade concluída e presença registrada. */
    @Transactional
    public CertificateResponse issue(Long activityId, Long authenticatedUserId, String authenticatedRole) {
        ensureParticipantRole(authenticatedRole);

        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Atividade não encontrada."));

        if (!Boolean.TRUE.equals(activity.getCompleted())) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "RN06: A atividade ainda não foi concluída; o certificado não pode ser emitido."
            );
        }

        Attendance attendance = attendanceRepository.findFirstByUserIdAndActivityId(authenticatedUserId, activityId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "RN06: Não há registro de presença (check-in) do participante nesta atividade."
                ));

        if (certificateRepository.existsByRegistrationId(attendance.getRegistrationId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Certificado já emitido para esta inscrição.");
        }

        try {
            Certificate saved = certificateRepository.saveAndFlush(new Certificate(
                    newValidationCode(),
                    attendance.getRegistrationId(),
                    authenticatedUserId,
                    activityId
            ));
            return CertificateResponse.fromEntity(saved, activity.getTitle());
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Certificado já emitido para esta inscrição.");
        }
    }

    /** Certificados do próprio participante (RN07). */
    @Transactional(readOnly = true)
    public List<CertificateResponse> listMine(Long authenticatedUserId, String authenticatedRole) {
        ensureParticipantRole(authenticatedRole);
        return certificateRepository.findByUserIdOrderByIssuedAtDesc(authenticatedUserId).stream()
                .map(c -> CertificateResponse.fromEntity(c, activityTitle(c.getActivityId())))
                .toList();
    }

    /** Validação pública de autenticidade pelo código. */
    @Transactional(readOnly = true)
    public CertificateResponse validate(String validationCode) {
        Certificate certificate = certificateRepository.findByValidationCode(validationCode)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Certificado não encontrado para o código informado."
                ));
        return CertificateResponse.fromEntity(certificate, activityTitle(certificate.getActivityId()));
    }

    private String activityTitle(Long activityId) {
        return activityRepository.findById(activityId).map(Activity::getTitle).orElse(null);
    }

    private void ensureParticipantRole(String authenticatedRole) {
        if (!"PARTICIPANT".equals(authenticatedRole)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "RN07 Violada: Apenas participantes podem emitir e consultar certificados próprios."
            );
        }
    }

    private static String newValidationCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 32).toUpperCase();
    }
}
