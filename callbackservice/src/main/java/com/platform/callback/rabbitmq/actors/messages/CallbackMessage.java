package com.platform.callback.rabbitmq.actors.messages;

import com.platform.callback.rabbitmq.ActionMessage;
import com.platform.callback.rabbitmq.ActionMessageVisitor;
import com.platform.callback.rabbitmq.actors.ActionType;
import lombok.Builder;
import lombok.Data;

/***
 Created by nitish.goyal on 05/02/19
 ***/
@Data
@Builder
public class CallbackMessage extends ActionMessage {

    private String requestId;

    protected CallbackMessage() {
        super(ActionType.CALLBACK);
    }

    @Builder
    public CallbackMessage(String requestId) {
        this();
        this.requestId = requestId;
    }

    @Override
    public <T> T accept(ActionMessageVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
