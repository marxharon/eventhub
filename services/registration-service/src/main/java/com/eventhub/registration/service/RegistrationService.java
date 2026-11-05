package com.eventhub.registration.service;

import com.eventhub.registration.dto.PaymentWebhookRequest;
import com.eventhub.registration.dto.RegistrationResponse;
import com.eventhub.registration.model.Activity;
import com.eventhub.registration.model.Registration;
import com.eventhub.registration.model.RegistrationStatus;
import com.eventhub.registration.repository.ActivityRepository;
import com.eventhub.registration.repository.RegistrationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class RegistrationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);

    private final ActivityRepository activityRepository;
    private final RegistrationRepository registrationRepository;
    private final ExternalIntegrationService external;
    private final TransactionTemplate transactions;
    private final Duration paymentWindow;

    public RegistrationService(
            ActivityRepository activityRepository,
            RegistrationRepository registrationRepository,
            ExternalIntegrationService external,
            TransactionTemplate transactions,
            @Value("${eventhub.registration.payment-window-minutes:15}") long paymentWindowMinutes
    ) {
        this.activityRepository = activityRepository;
        this.registrationRepository = registrationRepository;
        this.external = external;
        this.transactions = transactions;
        this.paymentWindow = Duration.ofMinutes(paymentWindowMinutes);
    }

    /**
     * Criação em duas fases: a reserva é commitada antes da chamada ao payment-service,
     * pois o payment-service valida a FK de registrations em outra conexão.
     */
    public RegistrationResponse create(Long userId, Long activityId) {
        Reservation reservation = transactions.execute(status -> reserve(userId, activityId));
        PendingPayment payment = reservation.payment();

        if (payment != null) {
            try {
                external.initiatePayment(payment.registrationId(), payment.userId(), payment.amount());
            } catch (RestClientException ex) {
                log.warn("Falha ao iniciar pagamento da inscrição {}: {}", payment.registrationId(), ex.getMessage());
                transactions.executeWithoutResult(status -> releaseAfterPaymentFailure(payment.registrationId()));
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Serviço de pagamento indisponível. A inscrição não foi efetivada.");
            }
        }
        return toResponse(findRegistration(reservation.registrationId()));
    }

    public List<RegistrationResponse> listMine(Long userId) {
        return registrationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(RegistrationResponse::from)
                .toList();
    }

    public RegistrationResponse getOwned(Long userId, Long registrationId) {
        Registration reg = findRegistration(registrationId);
        if (!reg.getUserId().equals(userId)) {
            throw forbidden("Apenas o titular pode consultar esta inscrição.");
        }
        return toResponse(reg);
    }

    public RegistrationResponse cancel(Long userId, Long registrationId) {
        transactions.executeWithoutResult(status -> {
            Locked locked = lockRegistration(registrationId);
            Registration reg = locked.registration();
            if (!reg.getUserId().equals(userId)) {
                throw forbidden("Apenas o titular pode cancelar esta inscrição.");
            }
            if (!reg.getStatus().isActive()) {
                throw conflict("Inscrição não está ativa e não pode ser cancelada.");
            }
            closeRegistration(locked.activity(), reg, RegistrationStatus.CANCELLED);
        });
        return toResponse(findRegistration(registrationId));
    }

    /** Recebe o resultado do pagamento do payment-service (APPROVED, REFUSED ou EXPIRED). */
    public RegistrationResponse applyPaymentResult(PaymentWebhookRequest request) {
        String result = request.paymentStatus().toUpperCase(Locale.ROOT);

        transactions.executeWithoutResult(status -> {
            Locked locked = lockRegistration(request.registrationId());
            Registration reg = locked.registration();
            if (reg.getStatus() != RegistrationStatus.PENDING_PAYMENT) {
                throw conflict("Inscrição não está aguardando pagamento.");
            }
            switch (result) {
                case "APPROVED" -> reg.changeStatus(RegistrationStatus.CONFIRMED);
                case "REFUSED" -> closeRegistration(locked.activity(), reg, RegistrationStatus.REFUSED);
                case "EXPIRED" -> closeRegistration(locked.activity(), reg, RegistrationStatus.EXPIRED);
                default -> throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Status de pagamento desconhecido: " + result);
            }
            log.info("Pagamento {} aplicado à inscrição {}: {}", request.paymentId(), request.registrationId(), result);
        });
        return toResponse(findRegistration(request.registrationId()));
    }

    /** Expira reservas PENDING_PAYMENT vencidas e libera a vaga (RN03). Chamado pelo job agendado. */
    public int expireOverdueReservations() {
        LocalDateTime now = LocalDateTime.now();
        List<Long> ids = registrationRepository.findExpiredIds(RegistrationStatus.PENDING_PAYMENT, now);
        for (Long id : ids) {
            try {
                transactions.executeWithoutResult(status -> {
                    Locked locked = lockRegistration(id);
                    Registration reg = locked.registration();
                    if (reg.getStatus() == RegistrationStatus.PENDING_PAYMENT
                            && reg.getExpiresAt() != null && reg.getExpiresAt().isBefore(now)) {
                        closeRegistration(locked.activity(), reg, RegistrationStatus.EXPIRED);
                    }
                });
            } catch (RuntimeException ex) {
                log.error("Falha ao expirar inscrição {}: {}", id, ex.getMessage());
            }
        }
        return ids.size();
    }

    // ---- Regras de negócio (executadas dentro de transação) ----

    private Reservation reserve(Long userId, Long activityId) {
        Activity activity = lockActivity(activityId);
        if (Boolean.TRUE.equals(activity.getCompleted())) {
            throw conflict("Atividade já concluída.");
        }
        if (registrationRepository.existsByUserIdAndActivityIdAndStatusIn(userId, activityId, RegistrationStatus.ACTIVE)) {
            throw conflict("Participante já possui inscrição ativa nesta atividade.");
        }

        RegistrationStatus status;
        LocalDateTime expiresAt = null;
        if (activity.getOccupiedSlots() < activity.getCapacity()) {
            activity.setOccupiedSlots(activity.getOccupiedSlots() + 1);
            if (activity.isFree()) {
                status = RegistrationStatus.CONFIRMED;
            } else {
                status = RegistrationStatus.PENDING_PAYMENT;
                expiresAt = LocalDateTime.now().plus(paymentWindow);
            }
        } else if (Boolean.TRUE.equals(activity.getWaitlistEnabled())) {
            status = RegistrationStatus.WAITLIST;
        } else {
            throw conflict("Atividade lotada e sem lista de espera.");
        }

        Registration reg = registrationRepository.save(new Registration(userId, activityId, status, expiresAt));
        external.notifyUser(userId, "REGISTRATION_" + status.name(), messageFor(status));

        PendingPayment payment = status == RegistrationStatus.PENDING_PAYMENT
                ? new PendingPayment(reg.getId(), userId, activity.getPrice())
                : null;
        return new Reservation(reg.getId(), payment);
    }

    /**
     * Encerra a inscrição e, se ela ocupava vaga, transfere a vaga ao primeiro da lista de espera
     * (RN04) ou devolve a vaga ao estoque. Requer atividade já bloqueada.
     */
    private void closeRegistration(Activity activity, Registration reg, RegistrationStatus newStatus) {
        boolean holdsSlot = reg.getStatus().holdsSlot();
        reg.changeStatus(newStatus);
        if (!holdsSlot) {
            return;
        }

        Optional<Registration> next = registrationRepository
                .findFirstByActivityIdAndStatusOrderByCreatedAtAscIdAsc(activity.getId(), RegistrationStatus.WAITLIST);
        if (next.isEmpty()) {
            activity.setOccupiedSlots(activity.getOccupiedSlots() - 1);
            return;
        }

        Registration promoted = next.get();
        if (activity.isFree()) {
            promoted.changeStatus(RegistrationStatus.CONFIRMED);
            external.notifyUser(promoted.getUserId(), "WAITLIST_PROMOTED",
                    "Uma vaga foi liberada e sua inscrição foi confirmada.");
            return;
        }

        promoted.awaitPayment(LocalDateTime.now().plus(paymentWindow));
        external.notifyUser(promoted.getUserId(), "WAITLIST_PROMOTED",
                "Uma vaga foi liberada para você. Efetue o pagamento em "
                        + paymentWindow.toMinutes() + " minutos.");
        startPaymentQuietly(new PendingPayment(promoted.getId(), promoted.getUserId(), activity.getPrice()));
    }

    private void releaseAfterPaymentFailure(Long registrationId) {
        Locked locked = lockRegistration(registrationId);
        if (locked.registration().getStatus() == RegistrationStatus.PENDING_PAYMENT) {
            closeRegistration(locked.activity(), locked.registration(), RegistrationStatus.CANCELLED);
        }
    }

    /** Falha em promoção não reverte a liberação de vaga; a reserva expira pelo job agendado. */
    private void startPaymentQuietly(PendingPayment payment) {
        try {
            external.initiatePayment(payment.registrationId(), payment.userId(), payment.amount());
        } catch (RestClientException ex) {
            log.warn("Pagamento da promoção {} não iniciado; será tratado pela expiração: {}",
                    payment.registrationId(), ex.getMessage());
        }
    }

    // ---- Auxiliares ----

    /** Ordem de bloqueio fixa: atividade primeiro, depois a inscrição (evita deadlock). */
    private Locked lockRegistration(Long registrationId) {
        Long activityId = registrationRepository.findActivityIdById(registrationId)
                .orElseThrow(() -> notFound("Inscrição não encontrada."));
        Activity activity = lockActivity(activityId);
        Registration reg = registrationRepository.findByIdForUpdate(registrationId)
                .orElseThrow(() -> notFound("Inscrição não encontrada."));
        return new Locked(reg, activity);
    }

    private Activity lockActivity(Long activityId) {
        return activityRepository.findByIdForUpdate(activityId)
                .orElseThrow(() -> notFound("Atividade não encontrada."));
    }

    private Registration findRegistration(Long registrationId) {
        return registrationRepository.findById(registrationId)
                .orElseThrow(() -> notFound("Inscrição não encontrada."));
    }

    private static RegistrationResponse toResponse(Registration reg) {
        return RegistrationResponse.from(reg);
    }

    private static String messageFor(RegistrationStatus status) {
        return switch (status) {
            case CONFIRMED -> "Sua inscrição foi confirmada.";
            case PENDING_PAYMENT -> "Sua vaga foi reservada. Efetue o pagamento para confirmar a inscrição.";
            case WAITLIST -> "A atividade está lotada. Você foi incluído na lista de espera.";
            default -> "Sua inscrição foi atualizada.";
        };
    }

    private static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private static ResponseStatusException forbidden(String message) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, message);
    }

    private record Locked(Registration registration, Activity activity) {}

    private record PendingPayment(Long registrationId, Long userId, BigDecimal amount) {}

    private record Reservation(Long registrationId, PendingPayment payment) {}
}
