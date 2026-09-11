package com.civic_connect.backend.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AllocationResponse {

    private boolean success;

    private Long recommended_worker_id;

    private Double match_score;

    private Double distance_km;

    private String reason;
}