package com.phonpham.ticketbooking.service;

import com.phonpham.ticketbooking.dto.BookingItemResponse;
import com.phonpham.ticketbooking.dto.BookingRequest;
import com.phonpham.ticketbooking.dto.BookingResponse;
import com.phonpham.ticketbooking.entity.*;
import com.phonpham.ticketbooking.exception.BadRequestException;
import com.phonpham.ticketbooking.exception.ResourceNotFoundException;
import com.phonpham.ticketbooking.exception.SeatNotAvailableException;
import com.phonpham.ticketbooking.messaging.BookingMessageProducer;
import com.phonpham.ticketbooking.messaging.BookingOrderMessage;
import com.phonpham.ticketbooking.repository.BookingRepository;
import com.phonpham.ticketbooking.repository.EventRepository;
import com.phonpham.ticketbooking.repository.SeatRepository;
import com.phonpham.ticketbooking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final SeatRepository seatRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final BookingMessageProducer messageProducer;

    /**
     * Create booking order and dispatch to RabbitMQ for asynchronous confirmation
     */
    @Transactional(rollbackFor = Exception.class)
    public BookingResponse createBooking(Long userId, BookingRequest request) {
        log.info("Creating booking for userId: {}, eventId: {}, seats: {}", userId, request.getEventId(), request.getSeatIds());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        Event event = eventRepository.findById(request.getEventId())
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + request.getEventId()));

        List<Seat> seats = seatRepository.findByIdIn(request.getSeatIds());
        if (seats.size() != request.getSeatIds().size()) {
            throw new BadRequestException("One or more seat IDs are invalid");
        }

        LocalDateTime now = LocalDateTime.now();
        BigDecimal totalAmount = BigDecimal.ZERO;

        // Verify user currently holds these seats
        for (Seat seat : seats) {
            if (seat.getStatus() == SeatStatus.BOOKED) {
                throw new SeatNotAvailableException("Seat " + seat.getSeatNumber() + " is already booked");
            }

            if (seat.getStatus() != SeatStatus.HELD || !userId.equals(seat.getHeldByUserId())) {
                throw new BadRequestException("Seat " + seat.getSeatNumber() + " must be reserved/held by you prior to booking");
            }

            if (seat.getHoldExpiresAt() != null && seat.getHoldExpiresAt().isBefore(now)) {
                throw new BadRequestException("Hold for seat " + seat.getSeatNumber() + " has expired. Please re-hold the seat.");
            }

            totalAmount = totalAmount.add(seat.getPrice());
        }

        String bookingReference = "TKT-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        Booking booking = Booking.builder()
                .bookingReference(bookingReference)
                .user(user)
                .event(event)
                .totalAmount(totalAmount)
                .status(BookingStatus.PENDING_PAYMENT)
                .items(new ArrayList<>())
                .build();

        for (Seat seat : seats) {
            BookingItem item = BookingItem.builder()
                    .booking(booking)
                    .seat(seat)
                    .price(seat.getPrice())
                    .build();
            booking.getItems().add(item);
        }

        Booking savedBooking = bookingRepository.save(booking);

        // Update available seats count on event
        event.setAvailableSeats(Math.max(0, event.getAvailableSeats() - seats.size()));
        eventRepository.save(event);

        // Dispatch asynchronous order creation to RabbitMQ
        BookingOrderMessage orderMessage = BookingOrderMessage.builder()
                .bookingId(savedBooking.getId())
                .bookingReference(savedBooking.getBookingReference())
                .userId(user.getId())
                .userEmail(user.getEmail())
                .eventId(event.getId())
                .eventTitle(event.getTitle())
                .seatIds(request.getSeatIds())
                .totalAmount(totalAmount)
                .paymentMethod(request.getPaymentMethod())
                .timestamp(LocalDateTime.now())
                .build();

        messageProducer.sendBookingOrderMessage(orderMessage);

        log.info("Booking {} created and queued to RabbitMQ for async fulfillment", bookingReference);

        return mapToBookingResponse(savedBooking);
    }

    @Transactional(readOnly = true)
    public BookingResponse getBookingByReference(Long userId, String bookingReference) {
        Booking booking = bookingRepository.findByBookingReference(bookingReference)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with reference: " + bookingReference));

        if (!booking.getUser().getId().equals(userId)) {
            throw new BadRequestException("You are not authorized to view this booking");
        }

        return mapToBookingResponse(booking);
    }

    @Transactional(readOnly = true)
    public Page<BookingResponse> getUserBookings(Long userId, Pageable pageable) {
        return bookingRepository.findByUserId(userId, pageable).map(this::mapToBookingResponse);
    }

    public BookingResponse mapToBookingResponse(Booking booking) {
        List<BookingItemResponse> items = new ArrayList<>();
        if (booking.getItems() != null) {
            for (BookingItem item : booking.getItems()) {
                items.add(BookingItemResponse.builder()
                        .id(item.getId())
                        .seatId(item.getSeat().getId())
                        .section(item.getSeat().getSection())
                        .rowNumber(item.getSeat().getRowNumber())
                        .seatNumber(item.getSeat().getSeatNumber())
                        .price(item.getPrice())
                        .build());
            }
        }

        return BookingResponse.builder()
                .id(booking.getId())
                .bookingReference(booking.getBookingReference())
                .userId(booking.getUser().getId())
                .username(booking.getUser().getUsername())
                .eventId(booking.getEvent().getId())
                .eventTitle(booking.getEvent().getTitle())
                .items(items)
                .totalAmount(booking.getTotalAmount())
                .status(booking.getStatus())
                .createdAt(booking.getCreatedAt())
                .build();
    }
}
