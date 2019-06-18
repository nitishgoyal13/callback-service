package com.platform.callback.core.services;

import com.platform.callback.common.exception.CallbackException;
import com.platform.callback.common.executor.CallbackExecutor;
import com.platform.callback.common.handler.CallbackHandler;
import com.utils.StringUtils;
import io.dropwizard.revolver.base.core.RevolverCallbackResponse;
import io.dropwizard.revolver.http.RevolverHttpCommand;
import io.dropwizard.revolver.persistence.PersistenceProvider;
import io.dropwizard.revolver.util.HeaderUtil;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import javax.ws.rs.core.Response;

/***
 Created by nitish.goyal on 23/05/19
 ***/
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Slf4j
@Data
public class DownstreamResponseHandler {

    private PersistenceProvider persistenceProvider;
    private CallbackHandler callbackHandler;
    private CallbackExecutor callbackExecutor;

    public void saveResponse(String requestId, RevolverCallbackResponse response, String path) {
        try {
            final val callbackRequest = persistenceProvider.request(requestId);
            if(callbackRequest == null) {
                throw new CallbackException(Response.Status.BAD_REQUEST, "Callback request not found");
            }

            if(StringUtils.isEmpty(path)) {
                log.info("Path is null, new path : " + callbackRequest.getCallbackUri());
                path = callbackRequest.getCallbackUri();
            }

            final String callMode = callbackRequest.getMode();
            log.info("Path : " + path + ", Callmode : " + callMode);

            val mailboxTtl = HeaderUtil.getTTL(callbackRequest);
            persistenceProvider.saveResponse(requestId, response, mailboxTtl);

            if(callMode != null && (callMode.equals(RevolverHttpCommand.CALL_MODE_CALLBACK) || callMode.equals(
                    RevolverHttpCommand.CALL_MODE_CALLBACK_SYNC))) {

                callbackExecutor.execute(requestId, response, path);
            }
        } catch (Exception e) {
            log.error("Error saving response : ", e);
            throw new CallbackException(Response.Status.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

}
