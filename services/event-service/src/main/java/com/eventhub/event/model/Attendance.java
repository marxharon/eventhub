package com.eventhub.event.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Presença registrada no check-in (RF09). Dono: event-service. */
@Entity
@Table(name = "attendances")
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "registration_id", nullable = false, unique = true)
    private Long registrationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "activity_id", nullable = false)
    private Long activityId;

    @Column(name = "checked_in_at", nullable = false)
    private LocalDateTime checkedInAt = LocalDateTime.now();

    protected Attendance() {}

    public Attendance(Long registrationId, Long userId, Long activityId) {
        this.registrationId = registrationId;
        this.userId = userId;
        this.activityId = activityId;
        this.checkedInAt = LocalDateTime.now();
    }

    public Long getId() { return id; }

    public Long getRegistrationId() { return registrationId; }

    public Long getUserId() { return userId; }

    public Long getActivityId() { return activityId; }

    public LocalDateTime getCheckedInAt() { return checkedInAt; }
}
