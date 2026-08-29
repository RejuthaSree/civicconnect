package com.civic_connect.backend.booking.entity;

import com.civic_connect.backend.common.enums.BookingStatus;
import com.civic_connect.backend.complaint.entity.Complaint;
import com.civic_connect.backend.user.entity.User;
import com.civic_connect.backend.worker.entity.Worker;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "citizen_id")
    private User citizen;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id")
    private Worker worker;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "issue_id")
    private Complaint issue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus bookingStatus = BookingStatus.PENDING;

    @Column(nullable = false)
    private Double amount;

    @Column(nullable = false)
    private boolean paymentRequired = true;

    @Column(updatable = false)
    private Instant createdAt;

    private Instant updatedAt;

    @PrePersist
    void created() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void updated() {
        updatedAt = Instant.now();
    }
}
