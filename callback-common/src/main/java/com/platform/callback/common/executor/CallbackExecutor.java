package com.platform.callback.common.executor;

import com.platform.callback.common.config.AppConfig;
import com.platform.callback.common.config.CallbackConfig;
import io.dropwizard.revolver.base.core.RevolverCallbackResponse;
import io.dropwizard.setup.Environment;

/***
 Created by nitish.goyal on 27/05/19
 ***/
public interface CallbackExecutor {

    void initialize(AppConfig configuration, Environment environment);

    void execute(String requestId, RevolverCallbackResponse response, String path);

    CallbackConfig.CallbackType getType();

}
