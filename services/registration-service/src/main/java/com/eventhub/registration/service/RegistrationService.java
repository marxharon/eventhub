package com.eventhub.registration.service;

import com.eventhub.registration.dto.PaymentWebhookRequest;
import com.eventhub.registration.dto.RegistrationResponse;
import com.eventhub.registration.model.Activity;
import com.eventhub.registration.model.EventRef;
import com.eventhub.registration.model.Registration;
import com.eventhub.registration.model.RegistrationStatus;
import com.eventhub.registration.repository.ActivityRepository;
import com.eventhub.registration.repository.EventRefRepository;
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
    private final EventRefRepository eventRefRepository;
    private final RegistrationRepository registrationRepository;
    private final ExternalIntegrationService external;
    private final TransactionTemplate transactions;
    private final Duration paymentWindow;

    public RegistrationService(
            ActivityRepository activityRepository,
            EventRefRepository eventRefRepository,
            RegistrationRepository registrationRepository,
            ExternalIntegrationService external,
            TransactionTemplate transactions,
            @Value("${eventhub.registration.payment-window-minutes:15}") long paymentWindowMinutes
    ) {
        this.activityRepository = activityRepository;
        this.eventRefRepository = eventRefRepository;
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
        final boolean[] hadPendingPayment = {false};
        transactions.executeWithoutResult(status -> {
            Locked locked = lockRegistration(registrationId);
            Registration reg = locked.registration();
            if (!reg.getUserId().equals(userId)) {
                throw forbidden("Apenas o titular pode cancelar esta inscrição.");
            }
            if (!reg.getStatus().isActive()) {
                throw conflict("Inscrição não está ativa e não pode ser cancelada.");
            }
            hadPendingPayment[0] = (reg.getStatus() == RegistrationStatus.PENDING_PAYMENT);
            closeRegistration(locked.activity(), reg, RegistrationStatus.CANCELLED);
            external.notifyUser(
                    userId,
                    "REGISTRATION_CANCELLED",
                    "Sua inscrição na atividade \"" + locked.activity().getTitle() + "\" foi cancelada com sucesso."
            );
        });
        if (hadPendingPayment[0]) {
            external.cancelPendingPayment(registrationId, "EXPIRED");
        }
        return toResponse(findRegistration(registrationId));
    }

    /** Recebe o resultado do pagamento do payment-service (APPROVED, REFUSED ou EXPIRED). */
    public RegistrationResponse applyPaymentResult(PaymentWebhookRequest request) {
        String result = request.paymentStatus().toUpperCase(Locale.ROOT);

        transactions.executeWithoutResult(status -> {
            Locked locked = lockRegistration(request.registrationId());
            Registration reg = locked.registration();
            if (reg.getStatus() != RegistrationStatus.PENDING_PAYMENT) {
                if (("APPROVED".equals(result) && reg.getStatus() == RegistrationStatus.CONFIRMED)
                        || ("REFUSED".equals(result) && reg.getStatus() == RegistrationStatus.REFUSED)
                        || ("EXPIRED".equals(result) && reg.getStatus() == RegistrationStatus.EXPIRED)) {
                    log.info("Webhook idempotente ignorado para inscrição {} já em {}", request.registrationId(), reg.getStatus());
                    return;
                }
                throw conflict("Inscrição não está aguardando pagamento.");
            }
            switch (result) {
                case "APPROVED" -> {
                    reg.changeStatus(RegistrationStatus.CONFIRMED);
                    locked.activity().setOccupiedSlots(locked.activity().getOccupiedSlots() + 1);
                }
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
                external.cancelPendingPayment(id, "EXPIRED");
            } catch (RuntimeException ex) {
                log.error("Falha ao expirar inscrição {}: {}", id, ex.getMessage());
            }
        }
        return ids.size();
    }

    /** Cancela todas as inscrições ativas das atividades de um evento e zera vagas ocupadas (RF02, RF05, RN07). */
    public int cancelAllByEvent(Long eventId, Long authenticatedUserId, String authenticatedRole) {
        EventRef event = eventRefRepository.findById(eventId)
                .orElseThrow(() -> notFound("Evento não encontrado."));
        if (authenticatedUserId == null
                || !"ORGANIZER".equals(authenticatedRole)
                || !event.getOrganizerId().equals(authenticatedUserId)) {
            throw forbidden("RN07 Violada: Você só pode administrar eventos sob sua própria responsabilidade.");
        }
        List<Long> pendingPaymentRegIds = new java.util.ArrayList<>();
        Integer count = transactions.execute(status -> {
            int cancelled = 0;
            List<Activity> activities = activityRepository.findByEventIdOrderByIdAsc(eventId);
            for (Activity act : activities) {
                Activity lockedAct = lockActivity(act.getId());
                List<Registration> activeRegs = registrationRepository
                        .findByActivityIdAndStatusIn(lockedAct.getId(), RegistrationStatus.ACTIVE);
                for (Registration reg : activeRegs) {
                    if (reg.getStatus() == RegistrationStatus.PENDING_PAYMENT) {
                        pendingPaymentRegIds.add(reg.getId());
                    }
                    reg.changeStatus(RegistrationStatus.CANCELLED);
                    cancelled++;
                }
                lockedAct.setOccupiedSlots(0);
            }
            return cancelled;
        });
        pendingPaymentRegIds.forEach(regId -> external.cancelPendingPayment(regId, "EXPIRED"));
        return count != null ? count : 0;
    }

    // ---- Regras de negócio (executadas dentro de transação) ----

    private Reservation reserve(Long userId, Long activityId) {
        Activity activity = lockActivity(activityId);
        EventRef event = eventRefRepository.findById(activity.getEventId())
                .orElseThrow(() -> notFound("Evento não encontrado."));
        if (!"PUBLISHED".equals(event.getStatus())) {
            throw conflict("Inscrições só são permitidas para eventos publicados (status atual: " + event.getStatus() + ").");
        }
        if (Boolean.TRUE.equals(activity.getCompleted())) {
            throw conflict("Atividade já concluída.");
        }
        if (registrationRepository.existsByUserIdAndActivityIdAndStatusIn(userId, activityId, RegistrationStatus.ACTIVE)) {
            throw conflict("Participante já possui inscrição ativa nesta atividade.");
        }

        RegistrationStatus status;
        LocalDateTime expiresAt = null;
        long reserved = activity.getOccupiedSlots()
                + registrationRepository.countByActivityIdAndStatus(activityId, RegistrationStatus.PENDING_PAYMENT);
        if (reserved < activity.getCapacity()) {
            if (activity.isFree()) {
                status = RegistrationStatus.CONFIRMED;
                activity.setOccupiedSlots(activity.getOccupiedSlots() + 1);
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
        RegistrationStatus previous = reg.getStatus();
        reg.changeStatus(newStatus);
        if (!previous.holdsSlot()) {
            return;
        }
        if (previous == RegistrationStatus.CONFIRMED) {
            activity.setOccupiedSlots(activity.getOccupiedSlots() - 1);
        }

        Optional<Registration> next = registrationRepository
                .findFirstByActivityIdAndStatusOrderByCreatedAtAscIdAsc(activity.getId(), RegistrationStatus.WAITLIST);
        if (next.isEmpty()) {
            return;
        }

        Registration promoted = next.get();
        if (activity.isFree()) {
            promoted.changeStatus(RegistrationStatus.CONFIRMED);
            activity.setOccupiedSlots(activity.getOccupiedSlots() + 1);
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
