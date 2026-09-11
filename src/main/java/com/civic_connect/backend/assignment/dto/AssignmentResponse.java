package com.civic_connect.backend.assignment.dto;
import com.civic_connect.backend.common.enums.CompletionStatus;
import java.time.Instant;
public record AssignmentResponse(Long id, Long complaintId, Long workerId, Instant assignedAt,
    Instant acceptedAt, Instant completedAt, String remarks, String proofImageUrl, String beforeImageUrl,
    String afterImageUrl, CompletionStatus completionStatus,
    Instant citizenVerifiedAt) { }
