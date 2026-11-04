package com.eventhub.event.dto;

import com.eventhub.event.model.Activity;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ActivityResponse {
    private Long id;
    private Long eventId;
    private String title;
    private String description;
    private String speaker;
    private String location;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer capacity;
    private Integer occupiedSlots;
    private Integer availableSlots;
    private BigDecimal price;
    private Boolean paid;
    private Boolean waitlistEnabled;
    private Boolean completed;

    public static ActivityResponse fromEntity(Activity a) {
        ActivityResponse dto = new ActivityResponse();
        dto.setId(a.getId());
        dto.setEventId(a.getEventId());
        dto.setTitle(a.getTitle());
        dto.setDescription(a.getDescription());
        dto.setSpeaker(a.getSpeaker());
        dto.setLocation(a.getLocation());
        dto.setStartTime(a.getStartTime());
        dto.setEndTime(a.getEndTime());
        dto.setCapacity(a.getCapacity());
        dto.setOccupiedSlots(a.getOccupiedSlots());
        dto.setAvailableSlots(Math.max(0, a.getCapacity() - a.getOccupiedSlots()));
        dto.setPrice(a.getPrice());
        dto.setPaid(a.getPrice() != null && a.getPrice().compareTo(BigDecimal.ZERO) > 0);
        dto.setWaitlistEnabled(a.getWaitlistEnabled());
        dto.setCompleted(a.getCompleted());
        return dto;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getEventId() { return eventId; }
    public void setEventId(Long eventId) { this.eventId = eventId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getSpeaker() { return speaker; }
    public void setSpeaker(String speaker) { this.speaker = speaker; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }

    public Integer getOccupiedSlots() { return occupiedSlots; }
    public void setOccupiedSlots(Integer occupiedSlots) { this.occupiedSlots = occupiedSlots; }

    public Integer getAvailableSlots() { return availableSlots; }
    public void setAvailableSlots(Integer availableSlots) { this.availableSlots = availableSlots; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public Boolean getPaid() { return paid; }
    public void setPaid(Boolean paid) { this.paid = paid; }

    public Boolean getWaitlistEnabled() { return waitlistEnabled; }
    public void setWaitlistEnabled(Boolean waitlistEnabled) { this.waitlistEnabled = waitlistEnabled; }

    public Boolean getCompleted() { return completed; }
    public void setCompleted(Boolean completed) { this.completed = completed; }
}
