package com.civic_connect.backend.worker.entity;

import com.civic_connect.backend.common.enums.VerificationStatus;
import com.civic_connect.backend.user.entity.User;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "worker_verifications")
@Getter
@Setter
@NoArgsConstructor
public class WorkerVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id", unique = true)
    private Worker worker;

    @Column(length = 2000)
    private String documentUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    private Instant approvedAt;
}
