package com.platform.callback.rabbitmq.actors.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.platform.callback.rabbitmq.actors.messages.ActionMessage;
import com.platform.callback.rabbitmq.actors.messages.CallbackMessage;
import io.dropwizard.actors.actor.ActorConfig;
import io.dropwizard.actors.connectivity.RMQConnection;
import io.dropwizard.revolver.base.core.RevolverCallbackResponse;
import io.dropwizard.revolver.callback.CallbackHandler;
import io.dropwizard.revolver.callback.InlineCallbackHandler;
import io.dropwizard.revolver.persistence.PersistenceProvider;
import lombok.extern.slf4j.Slf4j;

/***
 Created by nitish.goyal on 14/02/19
 ***/
@Slf4j
public class CallbackMessageHandlingActor extends MessageHandlingActor {

    private final PersistenceProvider persistenceProvider;
    private final CallbackHandler callbackHandler;

    @Inject
    public CallbackMessageHandlingActor(String queueId, ActorConfig config, RMQConnection connection, ObjectMapper mapper,
                                        InlineCallbackHandler callbackHandler, PersistenceProvider persistenceProvider) {
        super(queueId, config, connection, mapper);
        this.callbackHandler = callbackHandler;
        this.persistenceProvider = persistenceProvider;
    }


    @Override
    public boolean handle(ActionMessage message) {

        if(!(message instanceof CallbackMessage)) {
            return false;
        }
        CallbackMessage callbackMessage = (CallbackMessage)message;
        try {
            log.debug("Callback recon message:{}", message);
            String requestId = callbackMessage.getRequestId();
            RevolverCallbackResponse revolverCallbackResponse = persistenceProvider.response(requestId);
            callbackHandler.handle(requestId, revolverCallbackResponse);
            return true;
        } catch (Exception e) {
            log.error("Error in callback message reading", e);
            throw e;
        }
    }
}
