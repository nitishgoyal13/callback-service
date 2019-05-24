package com.platform.callback.rabbitmq;

import com.google.common.collect.Maps;
import com.platform.callback.exception.CallbackException;
import com.platform.callback.rabbitmq.actors.impl.MessageHandlingActor;
import com.platform.callback.rabbitmq.actors.messages.ActionMessage;
import lombok.extern.slf4j.Slf4j;

import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/***
 Created by nitish.goyal on 05/02/19
 ***/
@Slf4j
public class RMQActionMessagePublisher {

    private static Map<String, MessageHandlingActor> actors;

    public static void initialize(List<MessageHandlingActor> actorList) {
        actors = Maps.newConcurrentMap();
        actorList.forEach(actor -> {
            actors.put(actor.getQueueId(), actor);
        });
    }

    public static Map<String, MessageHandlingActor> getActors() {
        return Collections.unmodifiableMap(actors);
    }

    public static <Message extends ActionMessage> Boolean publishWithDelay(Message message, long delayInMillis, String clientIdKey) {
        try {
            MessageHandlingActor actor = actors.get(message.getQueueId());
            actor.publish(message);
            return true;
        } catch (Exception e) {
            String errorMessage = String.format("Error in publishing in rmq:%s", message.getQueueId());
            log.error(errorMessage, e);
            throw new CallbackException(Response.Status.INTERNAL_SERVER_ERROR, errorMessage);

        }
    }

    public static <Message extends ActionMessage> Boolean publish(Message message) {
        try {

            //TODO Implement retryer
            MessageHandlingActor actor = actors.get(message.getQueueId());
            if(actor == null) {
                return false;
            }
            log.info("Published message : " + message.toString());
            actor.publish(message);
            return true;
        } catch (Exception e) {
            String errorMessage = String.format("Error in publishing in rmq:%s ", message.getQueueId());
            log.error(errorMessage, e);
            throw new CallbackException(Response.Status.INTERNAL_SERVER_ERROR, errorMessage);
        }
    }
}
