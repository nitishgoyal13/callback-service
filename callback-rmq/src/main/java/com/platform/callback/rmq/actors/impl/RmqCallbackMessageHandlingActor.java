package com.platform.callback.rmq.actors.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.platform.callback.common.handler.CallbackHandler;
import com.platform.callback.rmq.actors.messages.ActionMessage;
import com.platform.callback.rmq.actors.messages.CallbackMessage;
import io.dropwizard.actors.actor.ActorConfig;
import io.dropwizard.actors.connectivity.RMQConnection;
import io.dropwizard.revolver.base.core.RevolverCallbackResponse;
import io.dropwizard.revolver.persistence.PersistenceProvider;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

/***
 Created by nitish.goyal on 14/02/19
 ***/
@Slf4j
@Data
@EqualsAndHashCode(callSuper = true)
public class RmqCallbackMessageHandlingActor extends MessageHandlingActor {

    private final PersistenceProvider persistenceProvider;
    private final CallbackHandler callbackHandler;

    @Inject
    public RmqCallbackMessageHandlingActor(String queueId, ActorConfig config, RMQConnection connection, ObjectMapper mapper,
                                           CallbackHandler callbackHandler, PersistenceProvider persistenceProvider) {
        super(queueId, config, connection, mapper);
        this.callbackHandler = callbackHandler;
        this.persistenceProvider = persistenceProvider;
    }


    @Override
    public boolean handle(ActionMessage message) {

        if(!(message instanceof CallbackMessage)) {
            log.error("Message class : " + message.getClass());
            return false;
        }
        CallbackMessage callbackMessage = (CallbackMessage)message;
        try {
            log.info("Callback recon message:{}", message);
            String requestId = callbackMessage.getRequestId();
            RevolverCallbackResponse revolverCallbackResponse = persistenceProvider.response(requestId);
            callbackHandler.handle(requestId, revolverCallbackResponse);
            return true;
        } catch (Exception e) {
            log.error("Error in handler message reading", e);
            throw e;
        }
    }
}
