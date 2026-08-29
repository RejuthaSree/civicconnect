package com.civic_connect.backend.review.repository;

import com.civic_connect.backend.review.entity.WorkerReview;
import com.civic_connect.backend.worker.entity.Worker;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkerReviewRepository extends JpaRepository<WorkerReview, Long> {
    boolean existsByAssignmentId(Long assignmentId);
    java.util.Optional<WorkerReview> findByAssignmentId(Long assignmentId);
    List<WorkerReview> findByWorkerOrderByCreatedAtDesc(Worker worker);
    long countByWorker(Worker worker);
}
