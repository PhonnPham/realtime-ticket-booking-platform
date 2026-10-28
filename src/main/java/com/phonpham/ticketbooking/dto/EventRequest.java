package com.phonpham.ticketbooking.dto;

import com.phonpham.ticketbooking.entity.EventStatus;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EventRequest {
    @NotBlank(message = "Event title is required")
    private String title;

    private String description;

    @NotBlank(message = "Venue name is required")
    private String venueName;

    @NotBlank(message = "Location is required")
    private String location;

    @NotNull(message = "Event date is required")
    @Future(message = "Event date must be in the future")
    private LocalDateTime eventDate;

    @NotNull(message = "Total seats is required")
    @Min(value = 1, message = "Total seats must be at least 1")
    private Integer totalSeats;

    private EventStatus status;

    private String imageUrl;
}
