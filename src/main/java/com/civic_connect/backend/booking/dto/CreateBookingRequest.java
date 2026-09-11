package com.civic_connect.backend.booking.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateBookingRequest(
        @NotNull Long workerId,
        @NotNull Long issueId,
        @NotNull @Positive Double amount) {
}
