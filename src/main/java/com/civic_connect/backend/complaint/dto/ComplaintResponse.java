package com.civic_connect.backend.complaint.dto;

import com.civic_connect.backend.common.enums.ComplaintStatus;
import com.civic_connect.backend.common.enums.IssueType;
import com.civic_connect.backend.common.enums.PriorityLevel;
import com.civic_connect.backend.common.enums.IssueScope;
import java.time.Instant;

public record ComplaintResponse(Long id, String title, String description, String address,
                                String area, String city, Double latitude, Double longitude, String imageUrl,
                                ComplaintStatus status, PriorityLevel priority, IssueType issueType, IssueScope issueScope, Instant reportedAt,
                                Instant resolvedAt, Integer upvotes, String aiClassification, Long reporterId, Long assignedWorkerId,
                                String aiCategory, String aiSeverity, String aiSuggestedDepartment, Double aiConfidence, Long aiConfirmedByAdmin) { }
