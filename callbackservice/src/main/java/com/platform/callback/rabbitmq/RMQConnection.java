package com.platform.callback.rabbitmq;

import com.google.common.collect.ImmutableMap;
import com.platform.callback.rabbitmq.config.RMQConfig;
import com.rabbitmq.client.Address;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import io.dropwizard.lifecycle.Managed;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Slf4j
@Data
public class RMQConnection implements Managed {
    private static final String QUEUE_PREFIX = "callback.";
    @Getter
    private final RMQConfig config;
    private Connection connection;
    private Channel channel;

    public RMQConnection(RMQConfig config) {
        this.config = config;
    }


    @Override
    public void start() throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setUsername(config.getUserName());
        factory.setPassword(config.getPassword());
        if(config.isSslEnabled()) {
            factory.useSslProtocol();
        }
        factory.setAutomaticRecoveryEnabled(true);
        factory.setTopologyRecoveryEnabled(true);
        factory.setNetworkRecoveryInterval(3000);
        factory.setRequestedHeartbeat(60);
        List<Address> addresses = config.getBrokers()
                .stream()
                .map(broker -> new Address(broker.getHost(), broker.getPort()))
                .collect(Collectors.toList());

        connection = factory.newConnection(Executors.newFixedThreadPool(config.getThreadPoolSize()),
                                           addresses.toArray(new Address[addresses.size()])
                                          );
        channel = connection.createChannel();
        initializeRMQ();
    }

    private void initializeRMQ() throws Exception {
        final String exchange = config.getExchange();
        final String deadLetterExchange = getSideline(exchange);
        channel.exchangeDeclare(exchange, "direct", true, false, rmqOpts());
        log.info("Exchange created {}", exchange);
        channel.exchangeDeclare(deadLetterExchange, "direct", true, false, rmqOpts());
        log.info("Exchange created {}", deadLetterExchange);

        for(MessageType messageType : MessageType.values()) {
            ensure(deriveQueueName(messageType, true), messageType.name(), deadLetterExchange, rmqOpts());
            ensure(deriveQueueName(messageType, false), messageType.name(), exchange, rmqOpts(deadLetterExchange));
        }
    }

    private <T extends Enum<T>> String deriveQueueName(T type, boolean sideline) {
        return QUEUE_PREFIX + (sideline ? getSideline(type.name()) : type.name());
    }


    private void ensure(final String queueName, final String routingQueue, String exchange, Map<String, Object> rmqOpts)
            throws Exception {
        channel.queueDeclare(queueName, true, false, false, rmqOpts);
        channel.queueBind(queueName, exchange, routingQueue);
        log.info("Created queue: {}", queueName);
    }

    private Map<String, Object> rmqOpts() {
        return ImmutableMap.<String, Object>builder().put("x-ha-policy", "all")
                .put("ha-mode", "all")
                .build();
    }

    private Map<String, Object> rmqOpts(String deadLetterExchange) {
        return ImmutableMap.<String, Object>builder().put("x-ha-policy", "all")
                .put("ha-mode", "all")
                .put("x-dead-letter-exchange", deadLetterExchange)
                .build();
    }

    @Override
    public void stop() {

    }

    public Channel channel() {
        return channel;
    }

    public Channel newChannel() throws IOException {
        return connection.createChannel();
    }

    private String getSideline(String name) {
        return String.format("%s_%s", name, "SIDELINE");
    }
}
