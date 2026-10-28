package com.phonpham.ticketbooking.service;

import com.phonpham.ticketbooking.dto.EventRequest;
import com.phonpham.ticketbooking.dto.EventResponse;
import com.phonpham.ticketbooking.dto.SeatResponse;
import com.phonpham.ticketbooking.entity.Event;
import com.phonpham.ticketbooking.entity.EventStatus;
import com.phonpham.ticketbooking.entity.Seat;
import com.phonpham.ticketbooking.entity.SeatStatus;
import com.phonpham.ticketbooking.exception.ResourceNotFoundException;
import com.phonpham.ticketbooking.repository.EventRepository;
import com.phonpham.ticketbooking.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final SeatRepository seatRepository;

    @Transactional(readOnly = true)
    public Page<EventResponse> getUpcomingEvents(Pageable pageable) {
        return eventRepository.findByStatusAndEventDateAfter(EventStatus.PUBLISHED, LocalDateTime.now(), pageable)
                .map(this::mapToEventResponse);
    }

    @Transactional(readOnly = true)
    public EventResponse getEventById(Long id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + id));
        return mapToEventResponse(event);
    }

    @Transactional
    public EventResponse createEvent(EventRequest request) {
        Event event = Event.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .venueName(request.getVenueName())
                .location(request.getLocation())
                .eventDate(request.getEventDate())
                .totalSeats(request.getTotalSeats())
                .availableSeats(request.getTotalSeats())
                .status(request.getStatus() != null ? request.getStatus() : EventStatus.PUBLISHED)
                .imageUrl(request.getImageUrl())
                .build();

        Event savedEvent = eventRepository.save(event);

        // Generate seats for the event
        List<Seat> seats = new ArrayList<>();
        int rows = (int) Math.ceil((double) request.getTotalSeats() / 10);
        int seatCounter = 0;

        for (int r = 1; r <= rows; r++) {
            char rowChar = (char) ('A' + (r - 1));
            for (int s = 1; s <= 10; s++) {
                if (seatCounter >= request.getTotalSeats()) break;
                seatCounter++;

                Seat seat = Seat.builder()
                        .event(savedEvent)
                        .section("Standard")
                        .rowNumber(String.valueOf(rowChar))
                        .seatNumber(String.valueOf(s))
                        .price(new BigDecimal("75.00"))
                        .status(SeatStatus.AVAILABLE)
                        .build();
                seats.add(seat);
            }
        }
        seatRepository.saveAll(seats);
        log.info("Created event id: {} with {} generated seats", savedEvent.getId(), seats.size());

        return mapToEventResponse(savedEvent);
    }

    @Transactional(readOnly = true)
    public List<SeatResponse> getSeatsForEvent(Long eventId) {
        return seatRepository.findByEventId(eventId).stream()
                .map(this::mapToSeatResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SeatResponse> getAvailableSeatsForEvent(Long eventId) {
        return seatRepository.findByEventIdAndStatus(eventId, SeatStatus.AVAILABLE).stream()
                .map(this::mapToSeatResponse)
                .toList();
    }

    public EventResponse mapToEventResponse(Event event) {
        return EventResponse.builder()
                .id(event.getId())
                .title(event.getTitle())
                .description(event.getDescription())
                .venueName(event.getVenueName())
                .location(event.getLocation())
                .eventDate(event.getEventDate())
                .status(event.getStatus())
                .totalSeats(event.getTotalSeats())
                .availableSeats(event.getAvailableSeats())
                .imageUrl(event.getImageUrl())
                .createdAt(event.getCreatedAt())
                .build();
    }

    public SeatResponse mapToSeatResponse(Seat seat) {
        return SeatResponse.builder()
                .id(seat.getId())
                .eventId(seat.getEvent().getId())
                .section(seat.getSection())
                .rowNumber(seat.getRowNumber())
                .seatNumber(seat.getSeatNumber())
                .price(seat.getPrice())
                .status(seat.getStatus())
                .heldByUserId(seat.getHeldByUserId())
                .holdExpiresAt(seat.getHoldExpiresAt())
                .build();
    }
}
