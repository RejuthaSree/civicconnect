package com.civic_connect.backend.booking.dto;

import jakarta.validation.constraints.NotNull;

public record CreateBookingRequest(
        @NotNull Long workerId,
        @NotNull Long issueId,
        Double amount) {
}
