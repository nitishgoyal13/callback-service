package com.platform.callback.rabbitmq.actors.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.platform.callback.rabbitmq.actors.messages.ActionMessage;
import io.dropwizard.actors.actor.ActorConfig;
import io.dropwizard.actors.actor.UnmanagedBaseActor;
import io.dropwizard.actors.connectivity.RMQConnection;
import io.dropwizard.actors.retry.RetryStrategyFactory;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ClassUtils;

import java.util.Set;


/**
 *
 */
@Slf4j
@Data
public abstract class MessageHandlingActor {

    private static final Set<Class<? extends Exception>> IGNORABLE_EXCEPTIONS = ImmutableSet.of(JsonProcessingException.class);

    private final UnmanagedBaseActor<ActionMessage> actorImpl;
    private final String queueId;

    @Inject
    public MessageHandlingActor(String queueId, ActorConfig config, RMQConnection connection, ObjectMapper mapper) {

        actorImpl = new UnmanagedBaseActor<>(queueId, config, connection, mapper, new RetryStrategyFactory(), ActionMessage.class,
                                             this::handle, t -> IGNORABLE_EXCEPTIONS.stream()
                .anyMatch(exceptionType -> ClassUtils.isAssignable(t.getClass(), exceptionType))
        );
        log.info("Created actor implementation for {}", queueId);
        this.queueId = queueId;
    }

    public <Message extends ActionMessage> void publish(Message message) throws Exception {
        actorImpl.publish(message);
    }

    public void start() throws Exception {
        actorImpl.start();
    }

    public void stop() throws Exception {
        actorImpl.stop();
    }

    public abstract <Message extends ActionMessage> boolean handle(Message message);


}
