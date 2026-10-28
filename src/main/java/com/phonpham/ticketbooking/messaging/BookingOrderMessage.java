package com.phonpham.ticketbooking.messaging;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingOrderMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long bookingId;
    private String bookingReference;
    private Long userId;
    private String userEmail;
    private Long eventId;
    private String eventTitle;
    private List<Long> seatIds;
    private BigDecimal totalAmount;
    private String paymentMethod;
    private LocalDateTime timestamp;
}
