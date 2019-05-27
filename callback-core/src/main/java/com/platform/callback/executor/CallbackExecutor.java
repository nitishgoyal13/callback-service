package com.platform.callback.executor;

import com.platform.callback.config.AppConfig;
import com.platform.callback.config.CallbackConfig;
import com.platform.callback.handler.CallbackHandler;
import io.dropwizard.revolver.base.core.RevolverCallbackResponse;
import io.dropwizard.revolver.persistence.PersistenceProvider;
import io.dropwizard.setup.Environment;

/***
 Created by nitish.goyal on 27/05/19
 ***/
public interface CallbackExecutor {

    void initialize(AppConfig configuration, Environment environment, CallbackHandler callbackHandler,
                    PersistenceProvider persistenceProvider);

    void execute(String requestId, RevolverCallbackResponse response, String path);

    CallbackConfig.CallbackType getType();

}
