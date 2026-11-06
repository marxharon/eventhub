package com.eventhub.registration.repository;

import com.eventhub.registration.model.Registration;
import com.eventhub.registration.model.RegistrationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RegistrationRepository extends JpaRepository<Registration, Long> {

    boolean existsByUserIdAndActivityIdAndStatusIn(Long userId, Long activityId, Collection<RegistrationStatus> statuses);

    long countByActivityIdAndStatus(Long activityId, RegistrationStatus status);

    List<Registration> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Registration> findFirstByActivityIdAndStatusOrderByCreatedAtAscIdAsc(Long activityId, RegistrationStatus status);

    @Query("select r.activityId from Registration r where r.id = :id")
    Optional<Long> findActivityIdById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Registration r where r.id = :id")
    Optional<Registration> findByIdForUpdate(@Param("id") Long id);

    @Query("select r.id from Registration r where r.status = :status and r.expiresAt < :now")
    List<Long> findExpiredIds(@Param("status") RegistrationStatus status, @Param("now") LocalDateTime now);

    List<Registration> findByActivityIdAndStatusIn(Long activityId, Collection<RegistrationStatus> statuses);

    /** Contagem de inscrições por atividade e status para o relatório gerencial (RF11). */
    @Query("select r.activityId, r.status, count(r) from Registration r " +
           "where r.activityId in :activityIds group by r.activityId, r.status")
    List<Object[]> countByActivityAndStatus(@Param("activityIds") Collection<Long> activityIds);
}
