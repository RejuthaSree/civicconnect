package com.civic_connect.backend.complaint.Repository;

import com.civic_connect.backend.complaint.entity.ComplaintVote;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComplaintVoteRepository extends JpaRepository<ComplaintVote, Long> {
    boolean existsByComplaintIdAndVoterId(Long complaintId, Long voterId);
}
