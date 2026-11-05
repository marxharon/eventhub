package com.eventhub.event.repository;

import com.eventhub.event.model.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    boolean existsByRegistrationId(Long registrationId);

    Optional<Attendance> findFirstByUserIdAndActivityId(Long userId, Long activityId);
}
