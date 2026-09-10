package com.civic_connect.backend.complaint.dto;

public record DuplicateCandidate(Long id, String title, Double similarity, Double distanceKm) {}
