package com.platform.callback.rabbitmq;

import com.platform.callback.rabbitmq.actors.messages.CallbackMessage;

public interface ActionMessageVisitor<T> {

    T visit (CallbackMessage callbackMessage);


}
