package com.platform.callback.services;

import com.platform.callback.App;
import com.platform.callback.config.CallbackConfig;
import com.platform.callback.exception.CallbackException;
import com.platform.callback.handler.CallbackHandler;
import com.platform.callback.rabbitmq.RMQActionMessagePublisher;
import com.platform.callback.rabbitmq.actors.messages.CallbackMessage;
import com.utils.StringUtils;
import io.dropwizard.revolver.base.core.RevolverCallbackResponse;
import io.dropwizard.revolver.http.RevolverHttpCommand;
import io.dropwizard.revolver.persistence.PersistenceProvider;
import io.dropwizard.revolver.util.HeaderUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.ws.rs.core.Response;

/***
 Created by nitish.goyal on 23/05/19
 ***/
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Slf4j
public class DownstreamResponseHandler {

    private PersistenceProvider persistenceProvider;
    private CallbackHandler callbackHandler;

    public void saveResponse(String requestId, RevolverCallbackResponse response, String path) {
        try {
            final val callbackRequest = persistenceProvider.request(requestId);
            if(callbackRequest == null) {
                log.error("Callback Request Not Found");
                throw new CallbackException(Response.Status.INTERNAL_SERVER_ERROR, "Callback request not found");
            }

            if(StringUtils.isEmpty(path)) {
                log.info("Path is null, new path : " + callbackRequest.getCallbackUri());
                path = callbackRequest.getCallbackUri();
            }

            log.info("Path : " + path);
            CallbackConfig.CallbackType callbackType = App.getCallbackType(path);
            log.info("CallbackType : " + callbackType);

            switch (callbackType) {

                case RMQ:

                    val mailboxTtl = HeaderUtil.getTTL(callbackRequest);
                    final String callMode = callbackRequest.getMode();
                    persistenceProvider.saveResponse(requestId, response, mailboxTtl);

                    if(callMode != null && (callMode.equals(RevolverHttpCommand.CALL_MODE_CALLBACK) || callMode.equals(
                            RevolverHttpCommand.CALL_MODE_CALLBACK_SYNC))) {
                        String queueId = App.getQueueId(path);
                        if(StringUtils.isEmpty(queueId)) {
                            throw new CallbackException(Response.Status.INTERNAL_SERVER_ERROR, "Queue not found for the path");
                        }
                        log.info("Inputting message to Queue with queueId : " + queueId);
                        RMQActionMessagePublisher.publish(CallbackMessage.builder()
                                                                  .requestId(requestId)
                                                                  .queueId(queueId)
                                                                  .build());
                    }
                    break;

                case INLINE:
                    log.info("Executing callback InLine");
                    callbackHandler.handle(requestId, response);
                    break;

                default:
                    break;
            }
        } catch (Exception e) {
            log.error("Error saving response : ", e);
            throw new CallbackException(Response.Status.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

}
