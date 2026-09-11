package com.civic_connect.backend.payment.entity;

import com.civic_connect.backend.booking.entity.Booking;
import com.civic_connect.backend.common.enums.PaymentSource;
import com.civic_connect.backend.common.enums.PaymentStatus;
import com.civic_connect.backend.user.entity.User;
import com.civic_connect.backend.worker.entity.Worker;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
public class Payment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", unique = true)
    private Booking booking;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "payer_id")
    private User payer;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id")
    private Worker worker;

    @Column(nullable = false)
    private Double amount;

    @Column(nullable = false, length = 3)
    private String currency = "INR";

    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private PaymentSource paymentSource;

    @Column(unique = true, nullable = false)
    private String razorpayOrderId;

    @Column(unique = true)
    private String razorpayPaymentId;

    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private PaymentStatus paymentStatus = PaymentStatus.CREATED;


    @Column(updatable = false) private Instant createdAt;
    private Instant updatedAt;

    @PrePersist void created() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }
    @PreUpdate void updated() {
        updatedAt = Instant.now();
    }
}
