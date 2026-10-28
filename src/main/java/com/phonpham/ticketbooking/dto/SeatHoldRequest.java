package com.phonpham.ticketbooking.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class SeatHoldRequest {
    @NotNull(message = "Event ID is required")
    private Long eventId;

    @NotEmpty(message = "At least one seat ID must be provided")
    private List<Long> seatIds;
}
