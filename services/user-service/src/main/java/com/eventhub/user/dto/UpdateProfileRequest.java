package com.eventhub.user.dto;

import jakarta.validation.constraints.NotBlank;

public class UpdateProfileRequest {

    @NotBlank(message = "Nome é obrigatório")
    private String name;

    private String organizationName;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getOrganizationName() { return organizationName; }
    public void setOrganizationName(String organizationName) { this.organizationName = organizationName; }
}
