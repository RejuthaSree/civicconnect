package com.civic_connect.backend.complaint.entity;


import com.civic_connect.backend.common.enums.ComplaintStatus;
import com.civic_connect.backend.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import com.civic_connect.backend.common.enums.IssueType;
import com.civic_connect.backend.common.enums.PriorityLevel;

@Entity
@Table(name = "complaints")
@Getter
@Setter
@NoArgsConstructor
public class Complaint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    private String description;

    private String location;

    private String address;
    private String area;
    private String city;
    private Double latitude;
    private Double longitude;

    private String imageUrl;

    @Enumerated(EnumType.STRING)
    private ComplaintStatus status;

    @Enumerated(EnumType.STRING)
    private PriorityLevel priority;

    @Enumerated(EnumType.STRING)
    private IssueType issueType;

    @Column(updatable = false)
    private Instant reportedAt;
    private Instant resolvedAt;
    private Integer upvotes = 0;
    private String aiClassification;
    private String duplicateGroupId;

    @ManyToOne
    private User reportedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_worker_id")
    private User assignedWorker;

    @PrePersist
    void initializeDefaults() {
        if (status == null) status = ComplaintStatus.REPORTED;
        if (priority == null) priority = PriorityLevel.MEDIUM;
        if (upvotes == null) upvotes = 0;
        if (reportedAt == null) reportedAt = Instant.now();
    }
}
