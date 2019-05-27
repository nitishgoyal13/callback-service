package com.platform.callback.rabbitmq;

import com.platform.callback.rabbitmq.actors.messages.CallbackMessage;

/***
 Created by nitish.goyal on 02/02/19
 ***/
public interface ActionMessageVisitor<T> {

    T visit(CallbackMessage callbackMessage);

}
