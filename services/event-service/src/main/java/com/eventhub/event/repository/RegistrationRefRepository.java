package com.eventhub.event.repository;

import com.eventhub.event.model.RegistrationRef;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RegistrationRefRepository extends JpaRepository<RegistrationRef, Long> {
}
