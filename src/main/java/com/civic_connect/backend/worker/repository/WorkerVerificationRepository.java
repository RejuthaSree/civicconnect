package com.civic_connect.backend.worker.repository;

import java.util.Optional;

import com.civic_connect.backend.worker.entity.WorkerVerification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkerVerificationRepository extends JpaRepository<WorkerVerification, Long> {

    Optional<WorkerVerification> findByWorkerId(Long workerId);
}
