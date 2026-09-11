package com.civic_connect.backend.review.dto;

import java.time.Instant;
public record WorkerReviewResponse(Long id, Long assignmentId, Long workerId, String complaintName,
                                   Integer rating, String comment, Instant createdAt) { }
