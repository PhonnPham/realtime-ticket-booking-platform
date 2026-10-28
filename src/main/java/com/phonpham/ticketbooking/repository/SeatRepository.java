package com.phonpham.ticketbooking.repository;

import com.phonpham.ticketbooking.entity.Seat;
import com.phonpham.ticketbooking.entity.SeatStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findByEventIdAndStatus(Long eventId, SeatStatus status);

    List<Seat> findByEventId(Long eventId);

    List<Seat> findByIdIn(List<Long> ids);

    @Query("SELECT s FROM Seat s WHERE s.status = 'HELD' AND s.holdExpiresAt < :now")
    List<Seat> findExpiredHeldSeats(@Param("now") LocalDateTime now);
}
