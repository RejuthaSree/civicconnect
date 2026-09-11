package com.civic_connect.backend.worker.dto;

import com.civic_connect.backend.common.enums.WorkerSkill;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record WorkerRegistrationRequest(@NotNull WorkerSkill skill, @NotBlank String serviceArea,
    Double latitude, Double longitude, Double workRadiusKm, String city, String phoneNumber,
    String address, String profilePhotoUrl, String aadhaarMasked, String governmentIdReference,
    String certificates, Integer experienceYears) { }
