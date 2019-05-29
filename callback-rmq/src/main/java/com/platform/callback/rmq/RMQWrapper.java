package com.platform.callback.rmq;

import com.google.inject.Inject;
import com.platform.callback.common.exception.RMQException;
import io.dropwizard.actors.connectivity.RMQConnection;
import io.dropwizard.lifecycle.Managed;
import lombok.extern.slf4j.Slf4j;

import javax.ws.rs.core.Response;

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
                        throw new RMQException(Response.Status.INTERNAL_SERVER_ERROR,
                                               "Error starting actor : " + messageHandlingActor.getQueueId() + e
                        );
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
                        throw new RMQException(Response.Status.INTERNAL_SERVER_ERROR,
                                               "Error stopping actor : " + messageHandlingActor.getQueueId() + e
                        );
                    }
                });
    }
}
