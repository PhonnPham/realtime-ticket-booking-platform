package com.phonpham.ticketbooking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Real-Time Event Ticket Booking Platform
 * Author: Pham Van Phon <vanphon741@gmail.com>
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class TicketBookingApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketBookingApplication.class, args);
    }
}
