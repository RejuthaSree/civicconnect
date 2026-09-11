package com.civic_connect.backend.ai.service;

import com.civic_connect.backend.ai.dto.AllocationRequest;
import com.civic_connect.backend.ai.dto.AllocationResponse;
import com.civic_connect.backend.ai.dto.WorkerInput;
import com.civic_connect.backend.complaint.entity.Complaint;
import com.civic_connect.backend.complaint.service.ComplaintService;
import com.civic_connect.backend.common.enums.VerificationStatus;
import com.civic_connect.backend.worker.entity.Worker;
import com.civic_connect.backend.worker.repository.WorkerRepository;
import com.civic_connect.backend.ai.dto.ForecastRequest;
import com.civic_connect.backend.ai.dto.ForecastResponse;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

@Service
public class AIService {

    private final WebClient webClient;
    private final ComplaintService complaintService;
    private final WorkerRepository workerRepository;

    public AIService(
            WebClient.Builder webClientBuilder,
            ComplaintService complaintService,
            WorkerRepository workerRepository) {

        this.webClient = webClientBuilder
                .baseUrl("http://localhost:8000")
                .build();

        this.complaintService = complaintService;
        this.workerRepository = workerRepository;
    }

    /*
     * Existing method:
     * Sends an allocation request to the Python ML service.
     */
    public AllocationResponse allocateWorker(AllocationRequest request) {

        return webClient.post()
                .uri("/allocate")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(AllocationResponse.class)
                .block();
    }

    /*
     * New method:
     * Gets the complaint and workers directly from the database,
     * then asks the Python service to recommend the best worker.
     */
    public AllocationResponse allocateWorker(Long complaintId) {

        // 1. Get complaint from database
        Complaint complaint = complaintService.get(complaintId);

        // 2. Get workers from database
        List<WorkerInput> workers = workerRepository
                .findAllByOrderByIdDesc()
                .stream()
                .filter(Worker::isAvailable)
                .filter(worker ->
                        worker.getVerificationStatus() == VerificationStatus.VERIFIED)
                .filter(worker ->
                        worker.getLatitude() != null &&
                        worker.getLongitude() != null)
                .map(this::toWorkerInput)
                .toList();

        // 3. Create request for Python
        AllocationRequest request = new AllocationRequest(
                complaint.getIssueType() != null
                        ? complaint.getIssueType().name()
                        : "OTHER",

                complaint.getLatitude(),
                complaint.getLongitude(),

                complaint.getPriority() != null
                        ? complaint.getPriority().name()
                        : "MEDIUM",

                workers
        );

        // 4. Send request to Python
        return allocateWorker(request);
    }
    public ForecastResponse forecastDemand(ForecastRequest request) {

    return webClient.post()
            .uri("/forecast")
            .bodyValue(request)
            .retrieve()
            .bodyToMono(ForecastResponse.class)
            .block();
}

    /*
     * Converts our Worker entity into the format
     * expected by the Python FastAPI service.
     */
    private WorkerInput toWorkerInput(Worker worker) {

        return new WorkerInput(
                worker.getId(),
                worker.getSkill() != null
                        ? worker.getSkill().name()
                        : "CONTRACTOR",

                worker.getLatitude(),
                worker.getLongitude(),

                worker.getWorkRadiusKm(),

                worker.isAvailable(),

                worker.getRating(),
                worker.getExperienceYears(),
                worker.getCompletedTasks(),

                worker.getVerificationStatus() != null
                        ? worker.getVerificationStatus().name()
                        : "PENDING"
        );
    }
}