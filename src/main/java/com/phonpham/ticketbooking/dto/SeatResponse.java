package com.phonpham.ticketbooking.dto;

import com.phonpham.ticketbooking.entity.SeatStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatResponse {
    private Long id;
    private Long eventId;
    private String section;
    private String rowNumber;
    private String seatNumber;
    private BigDecimal price;
    private SeatStatus status;
    private Long heldByUserId;
    private LocalDateTime holdExpiresAt;
}
