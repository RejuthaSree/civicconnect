package com.civic_connect.backend.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ForecastRequest {

    private String area;
    private String city;
    private String issue_type;
    private Integer hour;
    private Integer is_weekend;
    private Integer is_holiday;
    private Double previous_demand;
}