package com.phonpham.ticketbooking.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingMessageProducer {

    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.exchange:ticket.booking.exchange}")
    private String exchange;

    @Value("${app.rabbitmq.routing-key:ticket.booking.routingKey}")
    private String routingKey;

    public void sendBookingOrderMessage(BookingOrderMessage message) {
        log.info("Dispatching async BookingOrderMessage to RabbitMQ [exchange={}, routingKey={}, bookingRef={}]",
                exchange, routingKey, message.getBookingReference());
        rabbitTemplate.convertAndSend(exchange, routingKey, message);
    }
}
