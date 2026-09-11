package com.civic_connect.backend.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WorkerInput {

    private Long id;
    private String skill;

    private Double latitude;
    private Double longitude;

    private Double work_radius_km;

    private boolean available;

    private Double rating;
    private Integer experience_years;
    private Integer completed_tasks;

    private String verification_status;
}