package com.eventhub.registration.repository;

import com.eventhub.registration.model.EventRef;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRefRepository extends JpaRepository<EventRef, Long> {
}
