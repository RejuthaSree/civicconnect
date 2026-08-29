package com.civic_connect.backend.assignment.entity;

import com.civic_connect.backend.complaint.entity.Complaint;
import com.civic_connect.backend.worker.entity.Worker;
import com.civic_connect.backend.common.enums.CompletionStatus;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity @Table(name = "assignments") @Getter @Setter @NoArgsConstructor
public class Assignment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY) private Complaint complaint;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) private Worker worker;

    @Column(updatable = false) private Instant assignedAt;
    private Instant acceptedAt;
    private Instant completedAt;

    @Column(length = 2000) private String remarks;
    private String proofImageUrl;
    private String beforeImageUrl;
    private String afterImageUrl;
    @Enumerated(EnumType.STRING) private CompletionStatus completionStatus;
    private Instant citizenVerifiedAt;

    @PrePersist void created() { if (assignedAt == null) assignedAt = Instant.now(); }
}
