package com.platform.callback.rabbitmq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.callback.exception.CallbackException;
import com.platform.callback.exception.ResponseCode;
import com.platform.callback.hystrix.HystrixUtil;
import com.rabbitmq.client.MessageProperties;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RabbitMQCommands {
    private static RMQConnection connection;
    private static ObjectMapper mapper;

    private RabbitMQCommands() {}

    public static void initialize(RMQConnection rmqConnection, ObjectMapper objectMapper) {
        connection = rmqConnection;
        mapper = objectMapper;
    }

    public static <T extends Enum<T>> Boolean publish(T messageType, Object message) {
        try {
            return HystrixUtil.execute("RMQ", "publish", "", () -> {
                log.info("Publishing to {}: {}", messageType, message);
                connection.getChannel()
                        .basicPublish(connection.getConfig()
                                              .getExchange(), messageType.name(),
                                      MessageProperties.MINIMAL_PERSISTENT_BASIC, mapper.writeValueAsBytes(message)
                                     );
                return true;
            });
        } catch (Exception e) {
            log.error(String.format("Error while Publishing to %s: %s", messageType, message));
            throw new CallbackException(ResponseCode.QUEUE_EXCEPTION, "");
        }
    }
}
