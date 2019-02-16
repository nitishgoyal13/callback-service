package com.platform.callback.rabbitmq.actors.messages;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.platform.callback.rabbitmq.ActionMessageVisitor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;

/**
 * Base action message
 */
@Data
@EqualsAndHashCode
@ToString
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({@JsonSubTypes.Type(name = CallbackMessage.TYPE, value = CallbackMessage.class)})
public abstract class ActionMessage {

    @JsonProperty(value = "queueId", defaultValue = "DEFAULT")
    public String queueId = "DEFAULT";

    @JsonProperty(value = "type")
    private String type;

    public ActionMessage(String queueId, String type) {
        if(StringUtils.isNotEmpty(queueId)) {
            this.queueId = queueId;
        }
        this.type = type;
    }

    abstract public <T> T accept(ActionMessageVisitor<T> visitor);
}
