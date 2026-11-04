package com.eventhub.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public class EventRequest {

    @NotBlank(message = "Título do evento é obrigatório")
    private String title;

    private String description;

    @NotBlank(message = "Organização responsável é obrigatória (RF02)")
    private String organization;

    @NotNull(message = "Data/hora de início é obrigatória (RF02)")
    private LocalDateTime startDate;

    @NotNull(message = "Data/hora de término é obrigatória (RF02)")
    private LocalDateTime endDate;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getOrganization() { return organization; }
    public void setOrganization(String organization) { this.organization = organization; }

    public LocalDateTime getStartDate() { return startDate; }
    public void setStartDate(LocalDateTime startDate) { this.startDate = startDate; }

    public LocalDateTime getEndDate() { return endDate; }
    public void setEndDate(LocalDateTime endDate) { this.endDate = endDate; }
}
