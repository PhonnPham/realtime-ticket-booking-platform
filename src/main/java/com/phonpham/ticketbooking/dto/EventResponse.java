package com.phonpham.ticketbooking.dto;

import com.phonpham.ticketbooking.entity.EventStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventResponse {
    private Long id;
    private String title;
    private String description;
    private String venueName;
    private String location;
    private LocalDateTime eventDate;
    private EventStatus status;
    private Integer totalSeats;
    private Integer availableSeats;
    private String imageUrl;
    private LocalDateTime createdAt;
}
