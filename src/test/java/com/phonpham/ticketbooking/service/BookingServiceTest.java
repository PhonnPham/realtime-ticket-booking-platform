package com.phonpham.ticketbooking.service;

import com.phonpham.ticketbooking.dto.BookingRequest;
import com.phonpham.ticketbooking.dto.BookingResponse;
import com.phonpham.ticketbooking.entity.*;
import com.phonpham.ticketbooking.exception.BadRequestException;
import com.phonpham.ticketbooking.messaging.BookingMessageProducer;
import com.phonpham.ticketbooking.messaging.BookingOrderMessage;
import com.phonpham.ticketbooking.repository.BookingRepository;
import com.phonpham.ticketbooking.repository.EventRepository;
import com.phonpham.ticketbooking.repository.SeatRepository;
import com.phonpham.ticketbooking.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private SeatRepository seatRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private BookingMessageProducer messageProducer;

    @InjectMocks
    private BookingService bookingService;

    private User testUser;
    private Event testEvent;
    private Seat testSeat;
    private BookingRequest bookingRequest;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .username("phonpham")
                .email("vanphon741@gmail.com")
                .role(Role.ROLE_USER)
                .build();

        testEvent = Event.builder()
                .id(10L)
                .title("Imagine Dragons Concert")
                .venueName("Grand Arena")
                .location("Tokyo")
                .eventDate(LocalDateTime.now().plusDays(20))
                .totalSeats(100)
                .availableSeats(100)
                .build();

        testSeat = Seat.builder()
                .id(501L)
                .event(testEvent)
                .section("VIP")
                .rowNumber("A")
                .seatNumber("1")
                .price(new BigDecimal("150.00"))
                .status(SeatStatus.HELD)
                .heldByUserId(1L)
                .holdExpiresAt(LocalDateTime.now().plusMinutes(10))
                .build();

        bookingRequest = new BookingRequest();
        bookingRequest.setEventId(10L);
        bookingRequest.setSeatIds(List.of(501L));
        bookingRequest.setPaymentMethod("CREDIT_CARD");
    }

    @Test
    @DisplayName("Create booking succeeds and publishes async message to RabbitMQ")
    void createBooking_Success_DispatchesRabbitMessage() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(eventRepository.findById(10L)).thenReturn(Optional.of(testEvent));
        when(seatRepository.findByIdIn(List.of(501L))).thenReturn(List.of(testSeat));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> {
            Booking b = invocation.getArgument(0);
            b.setId(888L);
            return b;
        });

        BookingResponse response = bookingService.createBooking(1L, bookingRequest);

        assertNotNull(response);
        assertEquals(BookingStatus.PENDING_PAYMENT, response.getStatus());
        assertEquals(new BigDecimal("150.00"), response.getTotalAmount());

        verify(messageProducer, times(1)).sendBookingOrderMessage(any(BookingOrderMessage.class));
        verify(bookingRepository, times(1)).save(any(Booking.class));
        verify(eventRepository, times(1)).save(testEvent);
    }

    @Test
    @DisplayName("Create booking fails when user does not hold the seat")
    void createBooking_ThrowsBadRequestException_WhenUserDoesNotHoldSeat() {
        testSeat.setStatus(SeatStatus.AVAILABLE);
        testSeat.setHeldByUserId(null);

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(eventRepository.findById(10L)).thenReturn(Optional.of(testEvent));
        when(seatRepository.findByIdIn(List.of(501L))).thenReturn(List.of(testSeat));

        assertThrows(BadRequestException.class, () ->
                bookingService.createBooking(1L, bookingRequest)
        );

        verify(messageProducer, never()).sendBookingOrderMessage(any());
        verify(bookingRepository, never()).save(any());
    }
}
