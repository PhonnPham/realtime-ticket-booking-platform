package com.phonpham.ticketbooking.service;

import com.phonpham.ticketbooking.dto.SeatHoldRequest;
import com.phonpham.ticketbooking.dto.SeatHoldResponse;
import com.phonpham.ticketbooking.entity.Event;
import com.phonpham.ticketbooking.entity.Seat;
import com.phonpham.ticketbooking.entity.SeatStatus;
import com.phonpham.ticketbooking.exception.SeatAlreadyLockedException;
import com.phonpham.ticketbooking.exception.SeatNotAvailableException;
import com.phonpham.ticketbooking.repository.EventRepository;
import com.phonpham.ticketbooking.repository.SeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeatHoldServiceTest {

    @Mock
    private SeatRepository seatRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock mockLock;

    @InjectMocks
    private SeatHoldService seatHoldService;

    private Event testEvent;
    private Seat testSeat;
    private SeatHoldRequest holdRequest;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(seatHoldService, "holdTtlSeconds", 600L);
        ReflectionTestUtils.setField(seatHoldService, "lockWaitSeconds", 3L);
        ReflectionTestUtils.setField(seatHoldService, "lockLeaseSeconds", 10L);

        testEvent = Event.builder()
                .id(1L)
                .title("Coldplay Music of the Spheres Tour")
                .venueName("National Stadium")
                .location("Singapore")
                .eventDate(LocalDateTime.now().plusDays(30))
                .totalSeats(50000)
                .availableSeats(50000)
                .build();

        testSeat = Seat.builder()
                .id(101L)
                .event(testEvent)
                .section("VIP-A")
                .rowNumber("A")
                .seatNumber("1")
                .price(new BigDecimal("250.00"))
                .status(SeatStatus.AVAILABLE)
                .build();

        holdRequest = new SeatHoldRequest();
        holdRequest.setEventId(1L);
        holdRequest.setSeatIds(List.of(101L));
    }

    @Test
    @DisplayName("Hold seat succeeds with Redisson distributed lock")
    void holdSeats_Success_AcquiresDistributedLock() throws Exception {
        when(eventRepository.findById(1L)).thenReturn(Optional.of(testEvent));
        when(redissonClient.getLock("lock:seat:101")).thenReturn(mockLock);
        when(mockLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(mockLock.isHeldByCurrentThread()).thenReturn(true);
        when(seatRepository.findByIdIn(List.of(101L))).thenReturn(List.of(testSeat));

        SeatHoldResponse response = seatHoldService.holdSeats(99L, holdRequest);

        assertNotNull(response);
        assertEquals(SeatStatus.HELD, testSeat.getStatus());
        assertEquals(99L, testSeat.getHeldByUserId());
        assertNotNull(testSeat.getHoldExpiresAt());

        verify(mockLock, times(1)).tryLock(3L, 10L, TimeUnit.SECONDS);
        verify(mockLock, times(1)).unlock();
        verify(seatRepository, times(1)).saveAll(any());
    }

    @Test
    @DisplayName("Hold seat fails when Redisson distributed lock cannot be acquired")
    void holdSeats_ThrowsSeatAlreadyLockedException_WhenLockCannotBeAcquired() throws Exception {
        when(eventRepository.findById(1L)).thenReturn(Optional.of(testEvent));
        when(redissonClient.getLock("lock:seat:101")).thenReturn(mockLock);
        when(mockLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(false);

        assertThrows(SeatAlreadyLockedException.class, () ->
                seatHoldService.holdSeats(99L, holdRequest)
        );

        verify(seatRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Hold seat fails when seat is already booked")
    void holdSeats_ThrowsSeatNotAvailableException_WhenSeatAlreadyBooked() throws Exception {
        testSeat.setStatus(SeatStatus.BOOKED);

        when(eventRepository.findById(1L)).thenReturn(Optional.of(testEvent));
        when(redissonClient.getLock("lock:seat:101")).thenReturn(mockLock);
        when(mockLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(mockLock.isHeldByCurrentThread()).thenReturn(true);
        when(seatRepository.findByIdIn(List.of(101L))).thenReturn(List.of(testSeat));

        assertThrows(SeatNotAvailableException.class, () ->
                seatHoldService.holdSeats(99L, holdRequest)
        );

        verify(mockLock, times(1)).unlock();
        verify(seatRepository, never()).saveAll(any());
    }
}
