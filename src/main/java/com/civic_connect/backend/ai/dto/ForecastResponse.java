package com.civic_connect.backend.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ForecastResponse {

    private String area;
    private String city;
    private String issue_type;
    private Double predicted_demand;
}