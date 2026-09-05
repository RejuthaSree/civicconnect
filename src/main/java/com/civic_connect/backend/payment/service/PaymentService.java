package com.civic_connect.backend.payment.service;

import com.civic_connect.backend.booking.entity.Booking;
import com.civic_connect.backend.booking.service.BookingService;
import com.civic_connect.backend.common.exceptionHandler.ApiException;
import com.civic_connect.backend.common.enums.BookingStatus;
import com.civic_connect.backend.common.enums.PaymentSource;
import com.civic_connect.backend.common.enums.PaymentStatus;
import com.civic_connect.backend.common.enums.Role;
import com.civic_connect.backend.complaint.service.ComplaintService;
import com.civic_connect.backend.payment.dto.*;
import com.civic_connect.backend.payment.entity.Payment;
import com.civic_connect.backend.payment.repository.PaymentRepository;
import com.civic_connect.backend.user.entity.User;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PaymentService {

    private final PaymentRepository payments;
    private final BookingService bookings;
    private final ComplaintService complaints;
    private final String keyId;
    private final String keySecret;
    private final String currency;

    public PaymentService(
            PaymentRepository payments,
            BookingService bookings,
            ComplaintService complaints,
            @Value("${razorpay.key-id}") String keyId,
            @Value("${razorpay.key-secret}") String keySecret,
            @Value("${razorpay.currency:INR}") String currency) {
        this.payments = payments;
        this.bookings = bookings;
        this.complaints = complaints;
        this.keyId = keyId;
        this.keySecret = keySecret;
        this.currency = currency.toUpperCase();
    }

    public RazorpayOrderResponse createOrder(String email, CreateRazorpayOrderRequest request) {
        requireConfiguration();
        User payer = complaints.current(email);
        Booking booking = bookings.get(request.bookingId());
        PaymentSource source = request.paymentSource() == null ? PaymentSource.CITIZEN : request.paymentSource();
        validatePayer(payer, booking, source);
        validatePaymentEligibility(booking, request.amount());
        Payment existingPayment = payments.findByBookingId(booking.getId()).orElse(null);
        if (existingPayment != null) {
            if (existingPayment.getPaymentStatus() == PaymentStatus.SUCCESS) {
                throw new ApiException(HttpStatus.CONFLICT, "This booking has already been paid");
            }
            if (!existingPayment.getPayer().getId().equals(payer.getId())
                    || existingPayment.getPaymentSource() != source) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "A pending payment already exists for this booking");
            }
            // Checkout may have been closed or a test payment may have failed. Reuse the
            // already-created Razorpay order instead of blocking the citizen from retrying.
            return new RazorpayOrderResponse(existingPayment.getRazorpayOrderId(),
                    existingPayment.getAmount(), existingPayment.getCurrency(), keyId);
        }

        try {
            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", toMinorUnits(request.amount()));
            orderRequest.put("currency", currency);
            orderRequest.put("receipt", "booking_" + booking.getId());
            Order order = new RazorpayClient(keyId, keySecret).orders.create(orderRequest);

            Payment payment = new Payment();
            payment.setBooking(booking);
            payment.setPayer(payer);
            payment.setWorker(booking.getWorker());
            payment.setAmount(request.amount());
            payment.setCurrency(currency);
            payment.setPaymentSource(source);
            payment.setRazorpayOrderId(order.get("id"));
            payment.setPaymentStatus(PaymentStatus.PENDING);
            payments.save(payment);
            return new RazorpayOrderResponse(payment.getRazorpayOrderId(), payment.getAmount(), currency, keyId);
        } catch (RazorpayException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Razorpay could not create the order");
        }
    }

    public PaymentVerificationResponse verify(String email, VerifyRazorpayPaymentRequest request) {
        Payment payment = payments.findByRazorpayOrderId(request.razorpay_order_id())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Payment order not found"));
        if (!payment.getPayer().getEmail().equals(email)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the payment payer can verify this payment");
        }
        if (payment.getPaymentStatus() == PaymentStatus.SUCCESS) {
            return new PaymentVerificationResponse(true);
        }

        try {
            JSONObject attributes = new JSONObject();
            attributes.put("razorpay_order_id", request.razorpay_order_id());
            attributes.put("razorpay_payment_id", request.razorpay_payment_id());
            attributes.put("razorpay_signature", request.razorpay_signature());
            if (!Utils.verifyPaymentSignature(attributes, keySecret)) {
                payment.setPaymentStatus(PaymentStatus.FAILED);
                return new PaymentVerificationResponse(false);
            }
        } catch (RazorpayException exception) {
            payment.setPaymentStatus(PaymentStatus.FAILED);
            return new PaymentVerificationResponse(false);
        }

        payment.setRazorpayPaymentId(request.razorpay_payment_id());
        payment.setPaymentStatus(PaymentStatus.SUCCESS);
        payment.getBooking().setBookingStatus(BookingStatus.PAYMENT_COMPLETED);
        payment.getWorker().setTotalEarnings(payment.getWorker().getTotalEarnings() + payment.getAmount());
        if (payment.getPaymentSource() == PaymentSource.GOVERNMENT) {
            payment.getWorker().setTotalGovernmentPaymentsReceived(
                    payment.getWorker().getTotalGovernmentPaymentsReceived() + payment.getAmount());
        }
        return new PaymentVerificationResponse(true);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> history(String email) {
        User user = complaints.current(email);
        List<Payment> results = user.getRole() == Role.WORKER
                ? bookingsWorkerPayments(user)
                : payments.findByPayerIdOrWorkerIdOrderByCreatedAtDesc(user.getId(), -1L);
        return results.stream()
                .map(this::response)
                .toList();
    }

    private List<Payment> bookingsWorkerPayments(User user) {
        return payments.findAll().stream()
                .filter(payment -> payment.getWorker().getUser().getId().equals(user.getId()))
                .sorted((first, second) -> second.getCreatedAt().compareTo(first.getCreatedAt()))
                .toList();
    }

    private void validatePayer(User payer, Booking booking, PaymentSource source) {
        if (source == PaymentSource.CITIZEN && !booking.getCitizen().getId().equals(payer.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the booking citizen can make a citizen payment");
        }
        if (source == PaymentSource.GOVERNMENT && payer.getRole() != Role.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Government payments require ADMIN role");
        }
    }

    private void validatePaymentEligibility(Booking booking, Double amount) {
        if (booking.getBookingStatus() != BookingStatus.PAYMENT_PENDING) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Payment is available only after citizen completion confirmation");
        }
        if (!booking.isPaymentRequired()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This booking does not require payment");
        }
        if (amount == null || amount <= 0 || Double.compare(booking.getAmount(), amount) != 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Payment amount must match the booking amount");
        }
    }

    private void requireConfiguration() {
        if (keyId.isBlank() || keySecret.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Razorpay is not configured");
        }
    }

    private long toMinorUnits(Double amount) {
        return BigDecimal.valueOf(amount).movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    private PaymentResponse response(Payment payment) {
        return new PaymentResponse(payment.getId(), payment.getBooking().getId(), payment.getPayer().getId(),
                payment.getWorker().getId(), payment.getAmount(), payment.getCurrency(), payment.getPaymentSource(),
                payment.getPaymentStatus(), payment.getRazorpayOrderId(), payment.getRazorpayPaymentId(),
                payment.getCreatedAt(), payment.getUpdatedAt());
    }
}
