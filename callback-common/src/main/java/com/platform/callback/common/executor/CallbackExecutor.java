package com.platform.callback.common.executor;

import com.platform.callback.common.config.CallbackConfig;
import io.dropwizard.revolver.base.core.RevolverCallbackResponse;

/***
 Created by nitish.goyal on 27/05/19
 ***/
public interface CallbackExecutor {

    void execute(String requestId, RevolverCallbackResponse response, String path);

    CallbackConfig.CallbackType getType();

}
