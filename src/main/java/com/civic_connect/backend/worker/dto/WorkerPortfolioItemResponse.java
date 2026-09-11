package com.civic_connect.backend.worker.dto;

import java.time.Instant;
public record WorkerPortfolioItemResponse(Long assignmentId, String complaintName, String area,
    String beforeImageUrl, String afterImageUrl, Instant completionDate, Integer citizenRating,
    String citizenReview) { }
