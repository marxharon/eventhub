package com.eventhub.event.repository;

import com.eventhub.event.model.Event;
import com.eventhub.event.model.EventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {
    List<Event> findAllByOrderByStartDateAsc();
    List<Event> findByOrganizerIdOrderByStartDateAsc(Long organizerId);
    List<Event> findByStatusOrderByStartDateAsc(EventStatus status);
}
