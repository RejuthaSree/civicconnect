package com.civic_connect.backend.complaint.Repository;

import com.civic_connect.backend.common.enums.ComplaintStatus;
import com.civic_connect.backend.complaint.entity.Complaint;
import com.civic_connect.backend.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
