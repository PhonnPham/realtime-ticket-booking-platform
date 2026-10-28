package com.phonpham.ticketbooking.controller;

import com.phonpham.ticketbooking.dto.BookingRequest;
import com.phonpham.ticketbooking.dto.BookingResponse;
import com.phonpham.ticketbooking.security.UserPrincipal;
import com.phonpham.ticketbooking.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
@Tag(name = "Bookings & Async RabbitMQ Queue", description = "Ticket booking endpoints with asynchronous RabbitMQ pipeline")
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    @Operation(summary = "Confirm booking order and dispatch to RabbitMQ message broker")
    public ResponseEntity<BookingResponse> createBooking(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody BookingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bookingService.createBooking(userPrincipal.getId(), request));
    }

    @GetMapping("/my-bookings")
    @Operation(summary = "Get current authenticated user booking history")
    public ResponseEntity<Page<BookingResponse>> getMyBookings(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(bookingService.getUserBookings(userPrincipal.getId(), pageable));
    }

    @GetMapping("/{bookingReference}")
    @Operation(summary = "Get booking details by reference number")
    public ResponseEntity<BookingResponse> getBookingByReference(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable String bookingReference) {
        return ResponseEntity.ok(bookingService.getBookingByReference(userPrincipal.getId(), bookingReference));
    }
}
