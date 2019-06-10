package com.platform.callback.rmq.executor;

import com.platform.callback.common.config.CallbackConfig;
import com.platform.callback.common.exception.CallbackException;
import com.platform.callback.common.executor.CallbackExecutor;
import com.platform.callback.rmq.RMQActionMessagePublisher;
import com.platform.callback.rmq.actors.messages.CallbackMessage;
import com.utils.StringUtils;
import io.dropwizard.revolver.base.core.RevolverCallbackResponse;
import io.dropwizard.revolver.http.RevolverHttpCommand;
import io.dropwizard.revolver.persistence.PersistenceProvider;
import io.dropwizard.revolver.util.HeaderUtil;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.ws.rs.core.Response;

/***
 Created by nitish.goyal on 29/05/19
 ***/
@Slf4j
public class RmqCallbackExecutor implements CallbackExecutor {

    private PersistenceProvider persistenceProvider;

    @Override
    public void execute(String requestId, RevolverCallbackResponse response, String path) {

        final val callbackRequest = persistenceProvider.request(requestId);
        try {

            val mailboxTtl = HeaderUtil.getTTL(callbackRequest);
            final String callMode = callbackRequest.getMode();
            persistenceProvider.saveResponse(requestId, response, mailboxTtl);

            if(callMode != null && (callMode.equals(RevolverHttpCommand.CALL_MODE_CALLBACK) || callMode.equals(
                    RevolverHttpCommand.CALL_MODE_CALLBACK_SYNC))) {
                //TODO
                //String queueId = App.getQueueId(path);
                String queueId = "";
                if(StringUtils.isEmpty(queueId)) {
                    throw new CallbackException(Response.Status.INTERNAL_SERVER_ERROR, "Queue not found for the path");
                }
                log.info("Inputting message to Queue with queueId : " + queueId);
                RMQActionMessagePublisher.publish(CallbackMessage.builder()
                                                          .requestId(requestId)
                                                          .type(CallbackConfig.CallbackType.RMQ.name())
                                                          .queueId(queueId)
                                                          .build());

            }
        } catch (Exception e) {
            throw new CallbackException(Response.Status.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @Override
    public CallbackConfig.CallbackType getType() {
        return CallbackConfig.CallbackType.RMQ;
    }

}
