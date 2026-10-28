package com.phonpham.ticketbooking.messaging;

import com.phonpham.ticketbooking.entity.*;
import com.phonpham.ticketbooking.repository.BookingRepository;
import com.phonpham.ticketbooking.repository.PaymentRepository;
import com.phonpham.ticketbooking.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingMessageConsumer {

    private final BookingRepository bookingRepository;
    private final SeatRepository seatRepository;
    private final PaymentRepository paymentRepository;

    @RabbitListener(queues = "${app.rabbitmq.queue:ticket.booking.queue}")
    @Transactional(rollbackFor = Exception.class)
    public void processBookingOrder(BookingOrderMessage message) {
        log.info("Received async BookingOrderMessage from RabbitMQ for bookingRef: {}", message.getBookingReference());

        Booking booking = bookingRepository.findById(message.getBookingId()).orElse(null);
        if (booking == null) {
            log.error("Booking with id {} not found during async RabbitMQ processing", message.getBookingId());
            return;
        }

        // 1. Process mock payment transaction
        Payment payment = Payment.builder()
                .booking(booking)
                .paymentMethod(message.getPaymentMethod())
                .transactionId("TXN-" + UUID.randomUUID().toString().toUpperCase())
                .amount(message.getTotalAmount())
                .status("SUCCESS")
                .build();
        paymentRepository.save(payment);

        // 2. Finalize seat status from HELD to BOOKED
        List<Seat> seats = seatRepository.findByIdIn(message.getSeatIds());
        for (Seat seat : seats) {
            seat.setStatus(SeatStatus.BOOKED);
            seat.setHoldExpiresAt(null);
            seat.setHeldByUserId(null);
        }
        seatRepository.saveAll(seats);

        // 3. Confirm booking
        booking.setStatus(BookingStatus.CONFIRMED);
        bookingRepository.save(booking);

        // 4. Send notification (Mock email/SMS)
        log.info(">>> [NOTIFICATION SERVICE] Confirmation email sent to {} for booking reference: {} (Event: {}, Total: ${})",
                message.getUserEmail(), booking.getBookingReference(), message.getEventTitle(), message.getTotalAmount());
    }
}
