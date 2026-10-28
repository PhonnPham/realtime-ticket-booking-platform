package com.phonpham.ticketbooking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingItemResponse {
    private Long id;
    private Long seatId;
    private String section;
    private String rowNumber;
    private String seatNumber;
    private BigDecimal price;
}
