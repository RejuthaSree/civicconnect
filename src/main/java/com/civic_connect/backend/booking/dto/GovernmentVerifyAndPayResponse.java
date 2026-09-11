package com.civic_connect.backend.booking.dto;

import com.civic_connect.backend.payment.dto.RazorpayOrderResponse;

public record GovernmentVerifyAndPayResponse(
        BookingResponse booking,
        RazorpayOrderResponse razorpayOrder) {
}
