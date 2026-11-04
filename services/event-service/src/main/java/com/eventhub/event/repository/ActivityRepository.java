package com.eventhub.event.repository;

import com.eventhub.event.model.Activity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ActivityRepository extends JpaRepository<Activity, Long> {
    List<Activity> findByEventIdOrderByStartTimeAsc(Long eventId);
}
