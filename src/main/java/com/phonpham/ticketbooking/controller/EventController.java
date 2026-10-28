package com.phonpham.ticketbooking.controller;

import com.phonpham.ticketbooking.dto.EventRequest;
import com.phonpham.ticketbooking.dto.EventResponse;
import com.phonpham.ticketbooking.service.EventService;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
@Tag(name = "Events", description = "Event discovery and administrative management")
public class EventController {

    private final EventService eventService;

    @GetMapping
    @Operation(summary = "Get list of upcoming published events")
    public ResponseEntity<Page<EventResponse>> getUpcomingEvents(
            @PageableDefault(size = 10, sort = "eventDate", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(eventService.getUpcomingEvents(pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get event details by ID")
    public ResponseEntity<EventResponse> getEventById(@PathVariable Long id) {
        return ResponseEntity.ok(eventService.getEventById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a new event and auto-generate seat layout (ADMIN only)")
    public ResponseEntity<EventResponse> createEvent(@Valid @RequestBody EventRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(eventService.createEvent(request));
    }
}
