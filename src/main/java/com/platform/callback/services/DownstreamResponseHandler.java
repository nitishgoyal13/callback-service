package com.platform.callback.services;

import com.platform.callback.rabbitmq.ActionMessagePublisher;
import com.platform.callback.rabbitmq.actors.messages.CallbackMessage;
import io.dropwizard.revolver.base.core.RevolverCallbackResponse;
import io.dropwizard.revolver.http.RevolverHttpCommand;
import io.dropwizard.revolver.http.config.RevolverHttpApiConfig;
import io.dropwizard.revolver.http.model.RevolverHttpResponse;
import io.dropwizard.revolver.persistence.PersistenceProvider;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/***
 Created by nitish.goyal on 23/05/19
 ***/
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Slf4j
public class DownstreamResponseHandler {

    private PersistenceProvider persistenceProvider;

    public void saveResponse(String requestId, RevolverCallbackResponse response, final String callMode, final int ttl,
                              RevolverHttpApiConfig api) {
        try {

            persistenceProvider.saveResponse(requestId, response, ttl);

            if(callMode != null && (callMode.equals(RevolverHttpCommand.CALL_MODE_CALLBACK) || callMode.equals(
                    RevolverHttpCommand.CALL_MODE_CALLBACK_SYNC))) {
                String queueId = api.getCallbackQueueId();
                log.info("QueueId : " + queueId);
                ActionMessagePublisher.publish(CallbackMessage.builder()
                                                       .requestId(requestId)
                                                       .queueId(queueId)
                                                       .build());
            }
        } catch (Exception e) {
            log.error("Error saving response!", e);
        }

    }
}
