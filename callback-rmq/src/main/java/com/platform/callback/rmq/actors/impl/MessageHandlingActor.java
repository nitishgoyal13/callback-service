package com.platform.callback.rmq.actors.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.platform.callback.rmq.actors.messages.ActionMessage;
import io.dropwizard.actors.actor.ActorConfig;
import io.dropwizard.actors.actor.UnmanagedBaseActor;
import io.dropwizard.actors.connectivity.RMQConnection;
import io.dropwizard.actors.retry.RetryStrategyFactory;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ClassUtils;

import java.util.Set;

/***
 Created by nitish.goyal on 02/02/19
 ***/
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

    public <M extends ActionMessage> void publish(M message) throws Exception {
        actorImpl.publish(message);
    }

    public void start() throws Exception {
        actorImpl.start();
    }

    public void stop() throws Exception {
        actorImpl.stop();
    }

    public abstract <M extends ActionMessage> boolean handle(M message);


}
