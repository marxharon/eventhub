package com.eventhub.event.service;

import com.eventhub.event.model.Activity;
import com.eventhub.event.model.Attendance;
import com.eventhub.event.model.Certificate;
import com.eventhub.event.model.Event;
import com.eventhub.event.model.EventStatus;
import com.eventhub.event.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CertificateTimingTest {
    ActivityRepository activities;
    AttendanceRepository attendances;
    CertificateRepository certificates;
    NotificationClient notifications;
    CertificateService service;
    Activity activity;

    @BeforeEach
    void setUp() {
        activities = mock(ActivityRepository.class);
        attendances = mock(AttendanceRepository.class);
        certificates = mock(CertificateRepository.class);
        notifications = mock(NotificationClient.class);
        service = new CertificateService(certificates, attendances, activities, notifications);
        activity = new Activity();
        activity.setId(1L);
        activity.setEventId(10L);
        activity.setTitle("Minicurso");
        activity.setCapacity(10);
        activity.setCompleted(true);
        when(activities.findById(1L)).thenReturn(Optional.of(activity));
    }

    // Before start, during activity, after end. An old completed flag must not bypass the time rule.
    @ParameterizedTest
    @CsvSource({"1, 2, false", "-1, 1, false", "-2, -1, true"})
    void manualIssueRequiresEndAndAttendance(int start, int end, boolean allowed) {
        activity.setStartTime(LocalDateTime.now().plusHours(start));
        activity.setEndTime(LocalDateTime.now().plusHours(end));
        when(attendances.findFirstByUserIdAndActivityId(2L, 1L))
                .thenReturn(Optional.of(new Attendance(3L, 2L, 1L)));
        when(certificates.saveAndFlush(any(Certificate.class))).thenAnswer(inv -> inv.getArgument(0));
        if (allowed) {
            assertNotNull(service.issue(1L, 2L, "PARTICIPANT"));
            verify(certificates).saveAndFlush(any(Certificate.class));
        } else {
            assertEquals(422, assertThrows(ResponseStatusException.class,
                    () -> service.issue(1L, 2L, "PARTICIPANT")).getStatusCode().value());
            verify(certificates, never()).saveAndFlush(any());
        }
    }

    @ParameterizedTest
    @CsvSource({"1, 2, false", "-1, 1, false", "-2, -1, true"})
    void automaticIssueRequiresEnd(int start, int end, boolean allowed) {
        activity.setStartTime(LocalDateTime.now().plusHours(start));
        activity.setEndTime(LocalDateTime.now().plusHours(end));
        service.issueForAttendance(new Attendance(3L, 2L, 1L), activity);
        verify(certificates, times(allowed ? 1 : 0)).saveAndFlush(any(Certificate.class));
    }

    @ParameterizedTest
    @CsvSource({"1, 2, false", "-1, 1, false", "-2, -1, true"})
    void cannotCompleteBeforeEnd(int start, int end, boolean allowed) {
        activity.setStartTime(LocalDateTime.now().plusHours(start));
        activity.setEndTime(LocalDateTime.now().plusHours(end));
        activity.setCompleted(false);
        EventService events = mock(EventService.class);
        Event event = new Event();
        event.setStatus(EventStatus.PUBLISHED);
        when(events.getOwnedEventOrThrow(10L, 9L)).thenReturn(event);
        when(activities.save(activity)).thenReturn(activity);
        when(attendances.findByActivityId(1L)).thenReturn(List.of());
        ActivityService activityService = new ActivityService(activities, attendances,
                mock(RegistrationRefRepository.class), notifications, events, service);
        if (allowed) {
            activityService.completeActivity(1L, 9L, "ORGANIZER");
            assertTrue(activity.getCompleted());
        } else {
            assertEquals(422, assertThrows(ResponseStatusException.class,
                    () -> activityService.completeActivity(1L, 9L, "ORGANIZER")).getStatusCode().value());
            assertFalse(activity.getCompleted());
            verify(activities, never()).save(any());
        }
    }
}
