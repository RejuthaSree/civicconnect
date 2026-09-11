package com.civic_connect.backend.ai.controller;

import com.civic_connect.backend.ai.dto.AllocationRequest;
import com.civic_connect.backend.ai.dto.AllocationResponse;
import com.civic_connect.backend.ai.dto.ForecastRequest;
import com.civic_connect.backend.ai.dto.ForecastResponse;
import com.civic_connect.backend.ai.service.AIService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
public class AIController {

    private final AIService aiService;

    public AIController(AIService aiService) {
        this.aiService = aiService;
    }

    // Test endpoint using manually provided workers
    @PostMapping("/allocate")
    public AllocationResponse allocateWorker(
            @RequestBody AllocationRequest request) {

        return aiService.allocateWorker(request);
    }

    // Real endpoint using complaint and workers from the database
    @PostMapping("/allocate/{complaintId}")
    public AllocationResponse allocateWorkerForComplaint(
            @PathVariable Long complaintId) {

        return aiService.allocateWorker(complaintId);
    }

    // Demand forecasting endpoint
    @PostMapping("/forecast")
    public ForecastResponse forecastDemand(
            @RequestBody ForecastRequest request) {

        return aiService.forecastDemand(request);
    }
}