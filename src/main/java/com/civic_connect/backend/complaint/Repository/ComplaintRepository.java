package com.civic_connect.backend.complaint.Repository;

import com.civic_connect.backend.common.enums.ComplaintStatus;
import com.civic_connect.backend.complaint.entity.Complaint;
import com.civic_connect.backend.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComplaintRepository extends JpaRepository<Complaint, Long> {
    Page<Complaint> findByReportedBy(User user, Pageable pageable);
    Page<Complaint> findByStatus(ComplaintStatus status, Pageable pageable);
}
