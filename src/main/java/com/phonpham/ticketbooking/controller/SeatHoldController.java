package com.phonpham.ticketbooking.controller;

import com.phonpham.ticketbooking.dto.SeatHoldRequest;
import com.phonpham.ticketbooking.dto.SeatHoldResponse;
import com.phonpham.ticketbooking.dto.SeatResponse;
import com.phonpham.ticketbooking.security.UserPrincipal;
import com.phonpham.ticketbooking.service.EventService;
import com.phonpham.ticketbooking.service.SeatHoldService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/seats")
@RequiredArgsConstructor
@Tag(name = "Seats & Distributed Locking", description = "Real-time seat layout and Redisson distributed locking endpoints")
public class SeatHoldController {

    private final SeatHoldService seatHoldService;
    private final EventService eventService;

    @GetMapping("/event/{eventId}")
    @Operation(summary = "Get full seat map for an event")
    public ResponseEntity<List<SeatResponse>> getSeatsForEvent(@PathVariable Long eventId) {
        return ResponseEntity.ok(eventService.getSeatsForEvent(eventId));
    }

    @GetMapping("/event/{eventId}/available")
    @Operation(summary = "Get currently available seats for an event")
    public ResponseEntity<List<SeatResponse>> getAvailableSeatsForEvent(@PathVariable Long eventId) {
        return ResponseEntity.ok(eventService.getAvailableSeatsForEvent(eventId));
    }

    @PostMapping("/hold")
    @Operation(summary = "Hold/reserve seats using Redisson Distributed Lock (Zero Double-Booking)")
    public ResponseEntity<SeatHoldResponse> holdSeats(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody SeatHoldRequest request) {
        return ResponseEntity.ok(seatHoldService.holdSeats(userPrincipal.getId(), request));
    }

    @PostMapping("/release")
    @Operation(summary = "Manually release held seats")
    public ResponseEntity<Void> releaseSeats(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestBody List<Long> seatIds) {
        seatHoldService.releaseHeldSeats(userPrincipal.getId(), seatIds);
        return ResponseEntity.noContent().build();
    }
}
