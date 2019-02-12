package com.platform.callback.rabbitmq;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.platform.callback.rabbitmq.actors.ActionType;
import com.platform.callback.rabbitmq.actors.messages.CallbackMessage;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Base action message
 */
@Data
@EqualsAndHashCode
@ToString
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({@JsonSubTypes.Type(name = "CALLBACK", value = CallbackMessage.class)})
public abstract class ActionMessage {

    private final ActionType type;

    protected ActionMessage(ActionType type) {
        this.type = type;
    }

    abstract public <T> T accept(ActionMessageVisitor<T> visitor);
}
