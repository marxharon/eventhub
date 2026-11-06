package com.eventhub.event.repository;

import com.eventhub.event.model.RegistrationRef;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RegistrationRefRepository extends JpaRepository<RegistrationRef, Long> {

    @Query("SELECT DISTINCT r.userId FROM RegistrationRef r WHERE r.activityId = :activityId AND r.status IN ('CONFIRMED', 'PENDING_PAYMENT', 'WAITLIST')")
    List<Long> findActiveUserIdsByActivityId(@Param("activityId") Long activityId);

    @Query("SELECT DISTINCT r.userId FROM RegistrationRef r JOIN Activity a ON a.id = r.activityId WHERE a.eventId = :eventId AND r.status IN ('CONFIRMED', 'PENDING_PAYMENT', 'WAITLIST')")
    List<Long> findActiveUserIdsByEventId(@Param("eventId") Long eventId);
}
