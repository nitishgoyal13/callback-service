package com.platform.callback.rabbitmq;
/*
 * Copyright 2014 Flipkart Internet Pvt. Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.google.common.collect.Maps;
import com.platform.callback.exception.CallbackException;
import com.platform.callback.exception.ResponseCode;
import com.platform.callback.rabbitmq.actors.ActionType;
import io.dropwizard.actors.actor.Actor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/***
 Created by nitish.goyal on 05/02/19
 ***/
@Slf4j
public class ActionMessagePublisher {

    private static Map<ActionType, Actor> actors;

    public static void initialize(List<Actor> actorList) {
        actors = Maps.newConcurrentMap();
        actorList.forEach(actor -> {
            actors.put((ActionType)actor.getType(), actor);
        });
    }

    public static <Message extends ActionMessage> Boolean publishWithDelay(Message message, long delayInMillis,
                                                                           String clientIdKey) {
        try {
            Actor actor = actors.get(message.getType());
            actor.publishWithDelay(message, delayInMillis);
            return true;

        } catch (Exception e) {
            String errorMessage = String.format("Error in publishing in rmq:%s", message.getType());
            log.error(errorMessage, e);
            throw new CallbackException(ResponseCode.QUEUE_EXCEPTION, errorMessage);

        }
    }

    public static <Message extends ActionMessage> Boolean publish(Message message) {
        try {
            actors.get(message.getType())
                    .publish(message);
            return true;
        } catch (Exception e) {
            String errorMessage = String.format("Error in publishing in rmq:%s ", message.getType());
            log.error(errorMessage, e);
            throw new CallbackException(ResponseCode.QUEUE_EXCEPTION, errorMessage);
        }
    }
}
