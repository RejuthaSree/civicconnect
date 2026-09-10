package com.civic_connect.backend.classifier.dto;

import com.civic_connect.backend.common.enums.IssueType;
import com.civic_connect.backend.common.enums.PriorityLevel;

public record AiClassificationResult(
        IssueType category,
        PriorityLevel severity,
        String suggestedDepartment,
        Double confidence,
        boolean aiSuggested,
        Long confirmedByAdminId
) {}
