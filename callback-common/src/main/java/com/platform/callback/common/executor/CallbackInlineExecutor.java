package com.platform.callback.common.executor;

import com.google.inject.Inject;
import com.platform.callback.common.annotations.Executor;
import com.platform.callback.common.config.CallbackConfig;
import com.platform.callback.common.handler.CallbackHandler;
import io.dropwizard.revolver.base.core.RevolverCallbackResponse;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/***
 Created by nitish.goyal on 27/05/19
 ***/
@Slf4j
@NoArgsConstructor
@Builder
@Executor(callbackType = CallbackConfig.CallbackType.INLINE)
public class CallbackInlineExecutor implements CallbackExecutor {

    private CallbackHandler callbackHandler;

    @Inject
    public CallbackInlineExecutor(CallbackHandler callbackHandler) {
        this.callbackHandler = callbackHandler;
    }

    @Override
    public void execute(String requestId, RevolverCallbackResponse response, String path) {
        log.info("Executing callback InLine");
        callbackHandler.handle(requestId, response);
    }

    @Override
    public CallbackConfig.CallbackType getType() {
        return CallbackConfig.CallbackType.INLINE;
    }
}
