package com.phonpham.ticketbooking.service;

import com.phonpham.ticketbooking.dto.SeatHoldRequest;
import com.phonpham.ticketbooking.dto.SeatHoldResponse;
import com.phonpham.ticketbooking.entity.Event;
import com.phonpham.ticketbooking.entity.Seat;
import com.phonpham.ticketbooking.entity.SeatStatus;
import com.phonpham.ticketbooking.exception.BadRequestException;
import com.phonpham.ticketbooking.exception.ResourceNotFoundException;
import com.phonpham.ticketbooking.exception.SeatAlreadyLockedException;
import com.phonpham.ticketbooking.exception.SeatNotAvailableException;
import com.phonpham.ticketbooking.repository.EventRepository;
import com.phonpham.ticketbooking.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeatHoldService {

    private final SeatRepository seatRepository;
    private final EventRepository eventRepository;
    private final RedissonClient redissonClient;

    @Value("${app.booking.hold-ttl-seconds:600}")
    private long holdTtlSeconds;

    @Value("${app.booking.lock-wait-seconds:3}")
    private long lockWaitSeconds;

    @Value("${app.booking.lock-lease-seconds:10}")
    private long lockLeaseSeconds;

    /**
     * HIGH-CONCURRENCY DISTRIBUTED LOCKING SEAT HOLD
     *
     * 1. Acquires Redisson Distributed Locks on each target seat to serialize concurrent attempts.
     * 2. Sorts seat IDs to eliminate distributed deadlock risks.
     * 3. Validates seat availability and checks against existing active holds.
     * 4. Updates seat status to HELD with hold expiration TTL (e.g. 10 minutes).
     * 5. Absolutely prevents double-booking during massive traffic bursts.
     */
    @Transactional(rollbackFor = Exception.class)
    public SeatHoldResponse holdSeats(Long userId, SeatHoldRequest request) {
        Event event = eventRepository.findById(request.getEventId())
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + request.getEventId()));

        List<Long> seatIds = new ArrayList<>(request.getSeatIds());
        Collections.sort(seatIds); // Deadlock prevention

        List<RLock> acquiredLocks = new ArrayList<>();

        try {
            // Acquire distributed locks on all requested seats
            for (Long seatId : seatIds) {
                String lockKey = "lock:seat:" + seatId;
                RLock lock = redissonClient.getLock(lockKey);
                boolean locked = lock.tryLock(lockWaitSeconds, lockLeaseSeconds, TimeUnit.SECONDS);

                if (!locked) {
                    log.warn("Failed to acquire distributed lock for seatId: {}", seatId);
                    throw new SeatAlreadyLockedException("Seat " + seatId + " is currently being reserved by another user. Please retry.");
                }
                acquiredLocks.add(lock);
            }

            // Once all distributed locks are held, inspect database state
            List<Seat> seats = seatRepository.findByIdIn(seatIds);
            if (seats.size() != seatIds.size()) {
                throw new BadRequestException("One or more seat IDs are invalid");
            }

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime expiresAt = now.plusSeconds(holdTtlSeconds);

            for (Seat seat : seats) {
                if (!seat.getEvent().getId().equals(event.getId())) {
                    throw new BadRequestException("Seat " + seat.getId() + " does not belong to event " + event.getId());
                }

                if (seat.getStatus() == SeatStatus.BOOKED) {
                    throw new SeatNotAvailableException("Seat " + seat.getSeatNumber() + " is already booked");
                }

                // If currently HELD, verify if hold expired
                if (seat.getStatus() == SeatStatus.HELD) {
                    if (seat.getHoldExpiresAt() != null && seat.getHoldExpiresAt().isAfter(now)) {
                        if (!userId.equals(seat.getHeldByUserId())) {
                            throw new SeatAlreadyLockedException("Seat " + seat.getSeatNumber() + " is currently held by another user");
                        }
                    }
                }

                // Apply hold
                seat.setStatus(SeatStatus.HELD);
                seat.setHeldByUserId(userId);
                seat.setHoldExpiresAt(expiresAt);
            }

            seatRepository.saveAll(seats);

            log.info("Successfully acquired distributed locks and placed temporary hold on seats: {} for user: {}",
                    seatIds, userId);

            return SeatHoldResponse.builder()
                    .eventId(event.getId())
                    .userId(userId)
                    .heldSeatIds(seatIds)
                    .holdExpiresAt(expiresAt)
                    .holdTtlSeconds(holdTtlSeconds)
                    .message("Seats held successfully for " + (holdTtlSeconds / 60) + " minutes")
                    .build();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted while acquiring seat distributed locks", e);
        } finally {
            // Safely release all acquired distributed locks
            for (RLock lock : acquiredLocks) {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void releaseHeldSeats(Long userId, List<Long> seatIds) {
        List<Seat> seats = seatRepository.findByIdIn(seatIds);
        for (Seat seat : seats) {
            if (seat.getStatus() == SeatStatus.HELD && userId.equals(seat.getHeldByUserId())) {
                seat.setStatus(SeatStatus.AVAILABLE);
                seat.setHeldByUserId(null);
                seat.setHoldExpiresAt(null);
            }
        }
        seatRepository.saveAll(seats);
        log.info("Released seats {} for user: {}", seatIds, userId);
    }

    /**
     * Background scheduler to clean up expired seat holds every 30 seconds.
     */
    @Scheduled(fixedRate = 30000)
    @Transactional(rollbackFor = Exception.class)
    public void cleanupExpiredHolds() {
        LocalDateTime now = LocalDateTime.now();
        List<Seat> expiredSeats = seatRepository.findExpiredHeldSeats(now);
        if (!expiredSeats.isEmpty()) {
            for (Seat seat : expiredSeats) {
                seat.setStatus(SeatStatus.AVAILABLE);
                seat.setHeldByUserId(null);
                seat.setHoldExpiresAt(null);
            }
            seatRepository.saveAll(expiredSeats);
            log.info("Cleaned up {} expired held seats and reverted them to AVAILABLE", expiredSeats.size());
        }
    }
}
