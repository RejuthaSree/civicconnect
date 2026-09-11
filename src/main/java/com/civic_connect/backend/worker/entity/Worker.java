package com.civic_connect.backend.worker.entity;

import com.civic_connect.backend.common.enums.WorkerSkill;
import com.civic_connect.backend.common.enums.VerificationStatus;
import com.civic_connect.backend.user.entity.User;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "workers")
@Getter
@Setter
@NoArgsConstructor
public class Worker {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    @Enumerated(EnumType.STRING) @Column(nullable = false) private WorkerSkill skill;
    @Column(nullable = false) private String serviceArea;
    private String city;

    private String phoneNumber;

    @Column(length = 1000)
    private String address;

    private String profilePhotoUrl;

    /** Store only a masked/encrypted Aadhaar value; never expose a raw number. */
    private String aadhaarMasked;

    private String governmentIdReference;

    @Column(length = 4000)
    private String certificates;
    private Integer experienceYears = 0;

    @Enumerated(EnumType.STRING)
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;

    private Instant verifiedAt;

    private String verificationNotes;

    private Double latitude;

    private Double longitude;

    private Double workRadiusKm = 10.0;

    private boolean available = true;

    private Integer completedTasks = 0;

    private Double rating = 0.0;

    private Integer totalReviews = 0;

    private Double totalEarnings = 0.0;

    private Double totalGovernmentPaymentsReceived = 0.0;

}
