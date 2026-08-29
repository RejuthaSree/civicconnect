package com.civic_connect.backend.booking.dto;

import com.civic_connect.backend.common.enums.BookingStatus;
import java.time.Instant;

public record BookingResponse(
        Long id,
        Long citizenId,
        Long workerId,
        Long issueId,
        BookingStatus bookingStatus,
        Double amount,
        boolean paymentRequired,
        Instant createdAt,
        Instant updatedAt) {
}
