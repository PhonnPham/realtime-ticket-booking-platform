package com.phonpham.ticketbooking.repository;

import com.phonpham.ticketbooking.entity.Event;
import com.phonpham.ticketbooking.entity.EventStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {
    Page<Event> findByStatus(EventStatus status, Pageable pageable);
    Page<Event> findByStatusAndEventDateAfter(EventStatus status, LocalDateTime now, Pageable pageable);
}
