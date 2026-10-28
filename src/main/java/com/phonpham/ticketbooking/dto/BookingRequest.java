package com.phonpham.ticketbooking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class BookingRequest {
    @NotNull(message = "Event ID is required")
    private Long eventId;

    @NotEmpty(message = "Seat IDs cannot be empty")
    private List<Long> seatIds;

    @NotBlank(message = "Payment method is required")
    private String paymentMethod;
}
