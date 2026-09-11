package com.civic_connect.backend.payment.repository;
import java.util.List;
import java.util.Optional;

import com.civic_connect.backend.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByBookingId(Long bookingId);

    Optional<Payment> findByRazorpayOrderId(String razorpayOrderId);

    List<Payment> findByPayerIdOrWorkerIdOrderByCreatedAtDesc(Long payerId, Long workerId);

    List<Payment> findByWorkerIdOrderByCreatedAtDesc(Long workerId);
}
