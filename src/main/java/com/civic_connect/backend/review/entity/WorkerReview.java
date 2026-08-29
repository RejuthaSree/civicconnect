package com.civic_connect.backend.review.entity;

import com.civic_connect.backend.assignment.entity.Assignment;
import com.civic_connect.backend.worker.entity.Worker;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity @Table(name = "worker_reviews", uniqueConstraints = @UniqueConstraint(columnNames = "assignment_id"))
@Getter @Setter @NoArgsConstructor
public class WorkerReview {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    private Assignment assignment;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Worker worker;

    @Column(nullable = false)
    private Integer rating;

    @Column(length = 2000)
    private String comment;

    @Column(updatable = false)
    private Instant createdAt;

    @PrePersist void created() {
        if (createdAt == null)
            createdAt = Instant.now(); }
}
