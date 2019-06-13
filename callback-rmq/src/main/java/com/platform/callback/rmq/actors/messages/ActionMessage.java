package com.platform.callback.rmq.actors.messages;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.platform.callback.rmq.ActionMessageVisitor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
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
@NoArgsConstructor
public abstract class ActionMessage {

    public static final String DEFAULT_QUEUE_ID = "DEFAULT";

    @JsonProperty(value = "queueId", defaultValue = "DEFAULT")
    public String queueId = DEFAULT_QUEUE_ID;

    @JsonProperty(value = "type")
    private String type;

    ActionMessage(String queueId, String type) {
        if(StringUtils.isNotEmpty(queueId)) {
            this.queueId = queueId;
        }
        this.type = type;
    }

    public abstract <T> T accept(ActionMessageVisitor<T> visitor);
}
