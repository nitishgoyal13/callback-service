package com.platform.callback.rmq;

import com.platform.callback.rmq.actors.messages.CallbackMessage;

/***
 Created by nitish.goyal on 02/02/19
 ***/
public interface ActionMessageVisitor<T> {

    T visit(CallbackMessage callbackMessage);

}
