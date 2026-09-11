package com.civic_connect.backend.payment.dto;
import com.civic_connect.backend.common.enums.PaymentSource;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
public record CreateRazorpayOrderRequest(@NotNull Long bookingId,
                                         @NotNull @Positive Double amount,
                                         PaymentSource paymentSource) { }
