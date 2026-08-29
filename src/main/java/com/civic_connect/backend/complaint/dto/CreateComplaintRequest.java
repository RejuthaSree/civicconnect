package com.civic_connect.backend.complaint.dto;

import com.civic_connect.backend.common.enums.IssueType;
import com.civic_connect.backend.common.enums.PriorityLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateComplaintRequest(@NotBlank String title, @NotBlank String description,
    @NotBlank String address, @NotBlank String area, @NotBlank String city,
    @NotNull Double latitude, @NotNull Double longitude, String imageUrl,
    @NotNull IssueType issueType, PriorityLevel priority) { }
