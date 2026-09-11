package com.civic_connect.backend.complaint.Repository;

import com.civic_connect.backend.common.enums.ComplaintStatus;
import com.civic_connect.backend.common.enums.IssueType;
import com.civic_connect.backend.common.enums.PriorityLevel;
import com.civic_connect.backend.complaint.entity.Complaint;
import com.civic_connect.backend.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ComplaintRepository extends JpaRepository<Complaint, Long> {
    Page<Complaint> findByReportedBy(User user, Pageable pageable);
    Page<Complaint> findByStatus(ComplaintStatus status, Pageable pageable);

    List<Complaint> findByDuplicateGroupId(String duplicateGroupId);

    @Query("SELECT c FROM Complaint c WHERE c.status NOT IN (com.civic_connect.backend.common.enums.ComplaintStatus.RESOLVED, com.civic_connect.backend.common.enums.ComplaintStatus.REJECTED) AND c.id <> :excludeId AND (c.area = :area OR c.city = :city)")
    List<Complaint> findCandidateDuplicatesByAreaOrCity(
            @Param("excludeId") Long excludeId,
            @Param("area") String area,
            @Param("city") String city
    );

    @Query("SELECT c FROM Complaint c WHERE c.status NOT IN (com.civic_connect.backend.common.enums.ComplaintStatus.RESOLVED, com.civic_connect.backend.common.enums.ComplaintStatus.REJECTED) AND c.id <> :excludeId AND c.latitude BETWEEN :latMin AND :latMax AND c.longitude BETWEEN :lonMin AND :lonMax")
    List<Complaint> findCandidateDuplicatesByGeoBounds(
            @Param("excludeId") Long excludeId,
            @Param("latMin") Double latMin,
            @Param("latMax") Double latMax,
            @Param("lonMin") Double lonMin,
            @Param("lonMax") Double lonMax
    );

    long countByStatus(ComplaintStatus status);

    @Query("SELECT COUNT(c) FROM Complaint c WHERE c.resolvedAt IS NOT NULL AND c.resolvedAt >= :from")
    long countResolvedSince(@Param("from") Instant from);

    @Query("SELECT COUNT(c) FROM Complaint c WHERE c.reportedAt IS NOT NULL AND c.reportedAt >= :from")
    long countReportedSince(@Param("from") Instant from);

    @Query("SELECT COUNT(c) FROM Complaint c WHERE c.status IN (com.civic_connect.backend.common.enums.ComplaintStatus.REPORTED, com.civic_connect.backend.common.enums.ComplaintStatus.UNDER_REVIEW, com.civic_connect.backend.common.enums.ComplaintStatus.ASSIGNED, com.civic_connect.backend.common.enums.ComplaintStatus.WORK_ACCEPTED, com.civic_connect.backend.common.enums.ComplaintStatus.IN_PROGRESS, com.civic_connect.backend.common.enums.ComplaintStatus.WORK_COMPLETED, com.civic_connect.backend.common.enums.ComplaintStatus.CITIZEN_VERIFICATION) AND c.reportedAt IS NOT NULL AND c.reportedAt < :slaCutoff")
    long countSlaBreached(@Param("slaCutoff") Instant slaCutoff);

    @Query("SELECT c FROM Complaint c WHERE c.resolvedAt IS NOT NULL AND c.reportedAt IS NOT NULL")
    List<Complaint> findAllResolvedWithReportedAt();

    @Query("SELECT c FROM Complaint c WHERE c.latitude IS NOT NULL AND c.longitude IS NOT NULL " +
            "AND (:status IS NULL OR c.status = :status) " +
            "AND (:priority IS NULL OR c.priority = :priority) " +
            "AND (:issueType IS NULL OR c.issueType = :issueType) " +
            "AND (:area IS NULL OR c.area = :area) " +
            "AND (:from IS NULL OR c.reportedAt >= :from) " +
            "AND (:to IS NULL OR c.reportedAt <= :to)")
    List<Complaint> findAllWithCoordinates(
            @Param("status") ComplaintStatus status,
            @Param("priority") PriorityLevel priority,
            @Param("issueType") IssueType issueType,
            @Param("area") String area,
            @Param("from") Instant from,
            @Param("to") Instant to
    );
}
