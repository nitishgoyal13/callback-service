package com.platform.callback.rmq;

import com.google.common.collect.Maps;
import com.platform.callback.common.exception.CallbackException;
import com.platform.callback.rmq.actors.impl.MessageHandlingActor;
import com.platform.callback.rmq.actors.messages.ActionMessage;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/***
 Created by nitish.goyal on 05/02/19
 ***/
@Slf4j
@Builder
public class RMQActionMessagePublisher {


    private static Map<String, MessageHandlingActor> actors;

    private RMQActionMessagePublisher() {
    }

    public static void initialize(List<MessageHandlingActor> actorList) {
        actors = Maps.newConcurrentMap();
        actorList.forEach(actor -> actors.put(actor.getQueueId(), actor));
    }

    public static Map<String, MessageHandlingActor> getActors() {
        return Collections.unmodifiableMap(actors);
    }

    public static <M extends ActionMessage> void publish(M message) {
        try {
            //TODO Implement retryer
            MessageHandlingActor actor = actors.get(message.getQueueId());
            if(actor == null) {
                return;
            }
            log.info("Published message : " + message.toString());
            actor.publish(message);
        } catch (Exception e) {
            String errorMessage = String.format("Error in publishing in rmq:%s ", message.getQueueId());
            log.error(errorMessage, e);
            throw new CallbackException(Response.Status.INTERNAL_SERVER_ERROR, errorMessage);
        }
    }
}
