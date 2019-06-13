package com.platform.callback.rmq.actors.messages;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.platform.callback.rmq.ActionMessageVisitor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

/***
 Created by nitish.goyal on 05/02/19
 ***/
@Data
@EqualsAndHashCode(callSuper = true)
public class CallbackMessage extends ActionMessage {

    public static final String TYPE = "callback";

    @JsonProperty(value = "requestId")
    private String requestId;

    @Builder
    public CallbackMessage(@JsonProperty(value = "queueId") String queueId, @JsonProperty(value = "type") String type,
                           @JsonProperty(value = "requestId") String requestId) {
        super(queueId, TYPE);
        this.requestId = requestId;
    }


    @Override
    public <T> T accept(ActionMessageVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
