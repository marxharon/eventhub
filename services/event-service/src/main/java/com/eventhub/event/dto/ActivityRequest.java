package com.eventhub.event.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ActivityRequest {

    @NotBlank(message = "Título da atividade é obrigatório")
    private String title;

    private String description;

    @NotBlank(message = "Palestrante/responsável é obrigatório (RF03)")
    private String speaker;

    @NotBlank(message = "Local é obrigatório (RF03)")
    private String location;

    @NotNull(message = "Horário de início é obrigatório (RF03)")
    private LocalDateTime startTime;

    @NotNull(message = "Horário de término é obrigatório (RF03)")
    private LocalDateTime endTime;

    @NotNull(message = "Capacidade de vagas é obrigatória (RF03)")
    @Min(value = 1, message = "A capacidade deve ser de pelo menos 1 vaga")
    private Integer capacity;

    @NotNull(message = "Preço é obrigatório (0.00 para gratuita)")
    private BigDecimal price = BigDecimal.ZERO;

    private Boolean waitlistEnabled = true;

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

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public Boolean getWaitlistEnabled() { return waitlistEnabled; }
    public void setWaitlistEnabled(Boolean waitlistEnabled) { this.waitlistEnabled = waitlistEnabled; }
}
