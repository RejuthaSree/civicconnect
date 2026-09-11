package com.civic_connect.backend.assignment.repository;

import java.util.Optional;
import java.util.List;

import com.civic_connect.backend.assignment.entity.Assignment;
import com.civic_connect.backend.common.enums.CompletionStatus;
import com.civic_connect.backend.common.enums.IssueScope;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {
    Optional<Assignment> findByComplaintId(Long complaintId);

    List<Assignment> findByWorkerIdAndCompletionStatusOrderByCompletedAtDesc(Long workerId,
                                                                             CompletionStatus completionStatus);

    List<Assignment> findByWorkerIdOrderByAssignedAtDesc(Long workerId);

    List<Assignment> findByComplaintReportedByIdOrderByAssignedAtDesc(Long citizenId);

    long countByCompletionStatus(CompletionStatus completionStatus);

    long countByCompletionStatusAndComplaintIssueScope(CompletionStatus completionStatus, IssueScope scope);
}
