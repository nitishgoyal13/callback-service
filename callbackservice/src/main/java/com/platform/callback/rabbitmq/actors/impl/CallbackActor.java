package com.platform.callback.rabbitmq.actors.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;
import com.platform.callback.rabbitmq.actors.ActionType;
import com.platform.callback.rabbitmq.actors.messages.CallbackMessage;
import io.dropwizard.actors.actor.Actor;
import io.dropwizard.actors.actor.ActorConfig;
import io.dropwizard.actors.connectivity.RMQConnection;
import io.dropwizard.actors.retry.RetryStrategyFactory;
import io.dropwizard.revolver.base.core.RevolverCallbackResponse;
import io.dropwizard.revolver.callback.InlineCallbackHandler;
import io.dropwizard.revolver.persistence.PersistenceProvider;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CallbackActor extends Actor<ActionType, CallbackMessage> {

    private final PersistenceProvider persistenceProvider;
    private final InlineCallbackHandler callbackHandler;

    @Builder
    public CallbackActor(ActorConfig config, RMQConnection connection, ObjectMapper mapper,
                         RetryStrategyFactory retryStrategyFactory, PersistenceProvider persistenceProvider,
                         InlineCallbackHandler callbackHandler) {
        super(ActionType.CALLBACK, config, connection, mapper, retryStrategyFactory, CallbackMessage.class,
              ImmutableSet.of(JsonProcessingException.class)
             );
        this.persistenceProvider = persistenceProvider;
        this.callbackHandler = callbackHandler;
    }

    @Override
    public boolean handle(CallbackMessage message) throws Exception {
        try {
            log.debug("Callback recon message:{}", message);
            String requestId = message.getRequestId();
            RevolverCallbackResponse revolverCallbackResponse = persistenceProvider.response(requestId);
            callbackHandler.handle(requestId, revolverCallbackResponse);
            return true;
        } catch (Exception e) {
            log.error("Error in callback message reading", e);
            throw e;
        }
    }
}
