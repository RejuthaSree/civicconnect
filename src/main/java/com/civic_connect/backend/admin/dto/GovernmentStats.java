package com.civic_connect.backend.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public record GovernmentStats(
        long totalComplaints,
        long openComplaints,
        long inProgressComplaints,
        long resolvedToday,
        long resolvedLast7Days,
        long slaBreached,
        Double avgResolutionHours,
        long reportedToday,
        long reportedLast7Days,
        long totalWorkers,
        long verifiedWorkers,
        long activeAssignments,
        long completedPublicAssignments,
        long unpaidPublicAssignments,
        Map<String, Long> countsByStatus,
        Map<String, Long> countsByPriority,
        Map<String, Long> countsByIssueType,
        Map<String, Long> countsByArea,
        List<TopAreaStat> topAreas
) {
    public record TopAreaStat(String area, long complaints, long open, long resolved) {}
}
