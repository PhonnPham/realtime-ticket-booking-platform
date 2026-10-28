package com.phonpham.ticketbooking.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
@ConditionalOnClass(RabbitTemplate.class)
public class RabbitMQConfig {

    @Value("${app.rabbitmq.exchange:ticket.booking.exchange}")
    private String exchangeName;

    @Value("${app.rabbitmq.queue:ticket.booking.queue}")
    private String queueName;

    @Value("${app.rabbitmq.routing-key:ticket.booking.routingKey}")
    private String routingKey;

    @Value("${app.rabbitmq.dlq-exchange:ticket.dlq.exchange}")
    private String dlqExchangeName;

    @Value("${app.rabbitmq.dlq-queue:ticket.dlq.queue}")
    private String dlqQueueName;

    @Bean
    public DirectExchange bookingExchange() {
        return new DirectExchange(exchangeName, true, false);
    }

    @Bean
    public DirectExchange dlqExchange() {
        return new DirectExchange(dlqExchangeName, true, false);
    }

    @Bean
    public Queue dlqQueue() {
        return QueueBuilder.durable(dlqQueueName).build();
    }

    @Bean
    public Binding dlqBinding() {
        return BindingBuilder.bind(dlqQueue()).to(dlqExchange()).with(dlqQueueName);
    }

    @Bean
    public Queue bookingQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", dlqExchangeName);
        args.put("x-dead-letter-routing-key", dlqQueueName);
        return QueueBuilder.durable(queueName).withArguments(args).build();
    }

    @Bean
    public Binding bookingBinding() {
        return BindingBuilder.bind(bookingQueue()).to(bookingExchange()).with(routingKey);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        return rabbitTemplate;
    }
}
