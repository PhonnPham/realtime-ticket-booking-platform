package com.phonpham.ticketbooking.dto;

import com.phonpham.ticketbooking.entity.BookingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingResponse {
    private Long id;
    private String bookingReference;
    private Long userId;
    private String username;
    private Long eventId;
    private String eventTitle;
    private List<BookingItemResponse> items;
    private BigDecimal totalAmount;
    private BookingStatus status;
    private LocalDateTime createdAt;
}
