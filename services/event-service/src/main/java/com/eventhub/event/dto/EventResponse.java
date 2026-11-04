package com.eventhub.event.dto;

import com.eventhub.event.model.Event;
import com.eventhub.event.model.EventStatus;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class EventResponse {
    private Long id;
    private String title;
    private String description;
    private String organization;
    private Long organizerId;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private EventStatus status;
    private LocalDateTime createdAt;
    private List<ActivityResponse> activities = new ArrayList<>();

    public static EventResponse fromEntity(Event e, List<ActivityResponse> activities) {
        EventResponse dto = new EventResponse();
        dto.setId(e.getId());
        dto.setTitle(e.getTitle());
        dto.setDescription(e.getDescription());
        dto.setOrganization(e.getOrganization());
        dto.setOrganizerId(e.getOrganizerId());
        dto.setStartDate(e.getStartDate());
        dto.setEndDate(e.getEndDate());
        dto.setStatus(e.getStatus());
        dto.setCreatedAt(e.getCreatedAt());
        if (activities != null) {
            dto.setActivities(activities);
        }
        return dto;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getOrganization() { return organization; }
    public void setOrganization(String organization) { this.organization = organization; }

    public Long getOrganizerId() { return organizerId; }
    public void setOrganizerId(Long organizerId) { this.organizerId = organizerId; }

    public LocalDateTime getStartDate() { return startDate; }
    public void setStartDate(LocalDateTime startDate) { this.startDate = startDate; }

    public LocalDateTime getEndDate() { return endDate; }
    public void setEndDate(LocalDateTime endDate) { this.endDate = endDate; }

    public EventStatus getStatus() { return status; }
    public void setStatus(EventStatus status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public List<ActivityResponse> getActivities() { return activities; }
    public void setActivities(List<ActivityResponse> activities) { this.activities = activities; }
}
