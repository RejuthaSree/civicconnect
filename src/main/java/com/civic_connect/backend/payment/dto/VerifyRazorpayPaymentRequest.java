package com.civic_connect.backend.payment.dto;
import jakarta.validation.constraints.NotBlank;
public record VerifyRazorpayPaymentRequest(@NotBlank String razorpay_order_id, @NotBlank String razorpay_payment_id, @NotBlank String razorpay_signature) { }
