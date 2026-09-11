package com.civic_connect.backend.admin.dto;

public record GeoComplaint(
        Long id,
        String title,
        String status,
        String priority,
        String issueType,
        String area,
        String city,
        Double latitude,
        Double longitude,
        String reportedAt
) {}
