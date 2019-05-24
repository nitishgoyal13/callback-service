package com.platform.callback.config;

import io.dropwizard.actors.actor.ActorConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/***
 Created by nitish.goyal on 23/05/19
 ***/
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CallbackConfig {

    private List<CallbackPathConfig> callbackPathConfigs;

    Map<String, ActorConfig> actors;

    private CallbackType callbackType;

    public enum CallbackType {

        RMQ,
        INLINE

    }

}
