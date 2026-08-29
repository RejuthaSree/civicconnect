package com.civic_connect.backend.worker.dto;

import com.civic_connect.backend.common.enums.WorkerSkill;
import com.civic_connect.backend.common.enums.VerificationStatus;
public record WorkerResponse(Long id, Long userId, String name, WorkerSkill skill, String serviceArea,
    String city, String profilePhotoUrl, Integer experienceYears, String certificates,
    Double latitude, Double longitude, Double workRadiusKm, boolean available,
    VerificationStatus verificationStatus, Integer completedTasks, Double rating, Integer totalReviews,
    Double totalEarnings, Double totalGovernmentPaymentsReceived) { }
