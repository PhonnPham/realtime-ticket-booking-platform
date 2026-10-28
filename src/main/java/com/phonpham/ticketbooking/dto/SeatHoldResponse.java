package com.phonpham.ticketbooking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatHoldResponse {
    private Long eventId;
    private Long userId;
    private List<Long> heldSeatIds;
    private LocalDateTime holdExpiresAt;
    private long holdTtlSeconds;
    private String message;
}
