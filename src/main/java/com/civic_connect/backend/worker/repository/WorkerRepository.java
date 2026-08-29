package com.civic_connect.backend.worker.repository;

import com.civic_connect.backend.common.enums.WorkerSkill;
import com.civic_connect.backend.common.enums.VerificationStatus;
import com.civic_connect.backend.user.entity.User;
import java.util.List;
import java.util.Optional;

import com.civic_connect.backend.worker.entity.Worker;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkerRepository extends JpaRepository<Worker, Long> {
    Optional<Worker> findByUser(User user);

    List<Worker> findBySkillAndAvailableTrue(WorkerSkill skill);

    Page<Worker> findByAvailableTrueAndVerificationStatus(VerificationStatus status, Pageable pageable);

    Page<Worker> findBySkillAndAvailableTrueAndVerificationStatus(WorkerSkill skill, VerificationStatus status, Pageable pageable);
}
