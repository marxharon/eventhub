package com.eventhub.event.dto;

import com.eventhub.event.model.Attendance;

import java.time.LocalDateTime;

public record AttendanceResponse(
        Long id,
        Long registrationId,
        Long userId,
        Long activityId,
        LocalDateTime checkedInAt
) {
    public static AttendanceResponse fromEntity(Attendance attendance) {
        return new AttendanceResponse(
                attendance.getId(),
                attendance.getRegistrationId(),
                attendance.getUserId(),
                attendance.getActivityId(),
                attendance.getCheckedInAt()
        );
    }
}
