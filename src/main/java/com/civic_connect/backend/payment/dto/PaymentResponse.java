package com.civic_connect.backend.payment.dto;
import com.civic_connect.backend.common.enums.PaymentSource;
import com.civic_connect.backend.common.enums.PaymentStatus;
import java.time.Instant;
public record PaymentResponse(Long id, Long bookingId, Long payerId, Long workerId, Double amount, String currency, PaymentSource paymentSource, PaymentStatus paymentStatus, String razorpayOrderId, String razorpayPaymentId, Instant createdAt, Instant updatedAt) { }
