package com.platform.callback.rabbitmq;

import com.google.inject.Inject;
import io.dropwizard.actors.connectivity.RMQConnection;
import io.dropwizard.lifecycle.Managed;
import lombok.extern.slf4j.Slf4j;

/**
 * This is a proxy class to ensure guice picks up the RMQ connection class.
 */
@Slf4j
public class RMQWrapper implements Managed {

    private final RMQConnection connection;

    @Inject
    public RMQWrapper(RMQConnection connection) {
        this.connection = connection;
    }

    @Override
    public void start() throws Exception {
        connection.start();
        RMQActionMessagePublisher.getActors()
                .forEach((s, messageHandlingActor) -> {
                    try {
                        messageHandlingActor.start();
                    } catch (Exception e) {
                        log.error("Error starting actor : " + messageHandlingActor.getQueueId(), e);
                        throw new RuntimeException("Error starting actor : " + messageHandlingActor.getQueueId(), e);
                    }
                });
    }

    @Override
    public void stop() throws Exception {
        connection.stop();
        RMQActionMessagePublisher.getActors()
                .forEach((s, messageHandlingActor) -> {
                    try {
                        messageHandlingActor.stop();
                    } catch (Exception e) {
                        log.error("Error stopping actor : " + messageHandlingActor.getQueueId(), e);
                        throw new RuntimeException("Error stopping actor : " + messageHandlingActor.getQueueId(), e);
                    }
                });
    }
}
