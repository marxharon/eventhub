package com.eventhub.registration.repository;

import com.eventhub.registration.model.AttendanceRef;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface AttendanceRefRepository extends JpaRepository<AttendanceRef, Long> {

    /** Total de check-ins (comparecimentos) por atividade (RF11). Retorna linhas [activityId, count]. */
    @Query("select a.activityId, count(a) from AttendanceRef a where a.activityId in :activityIds group by a.activityId")
    List<Object[]> countByActivity(@Param("activityIds") Collection<Long> activityIds);
}
