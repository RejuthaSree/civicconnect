package com.civic_connect.backend.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AllocationRequest {

    private String issue_type;

    private Double latitude;
    private Double longitude;

    private String priority;

    private List<WorkerInput> workers;
}