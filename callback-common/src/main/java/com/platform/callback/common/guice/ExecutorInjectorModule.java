package com.platform.callback.common.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.platform.callback.common.config.CallbackConfig;
import com.platform.callback.common.executor.CallbackExecutorFactory;
import com.platform.callback.common.handler.CallbackHandler;
import com.platform.callback.common.handler.InlineCallbackHandler;
import io.dropwizard.revolver.persistence.PersistenceProvider;

/***
 Created by nitish.goyal on 27/05/19
 ***/
public class ExecutorInjectorModule extends AbstractModule {

    private CallbackHandler callbackHandler;
    private CallbackConfig callbackConfig;
    private PersistenceProvider persistenceProvider;

    public ExecutorInjectorModule(InlineCallbackHandler callbackHandler, CallbackConfig callbackConfig,
                                  PersistenceProvider persistenceProvider) {
        this.callbackHandler = callbackHandler;
        this.callbackConfig = callbackConfig;
        this.persistenceProvider = persistenceProvider;
    }

    @Override
    protected void configure() {
        bind(CallbackExecutorFactory.class).in(Scopes.SINGLETON);
        bind(CallbackHandler.class).toInstance(callbackHandler);
        bind(CallbackConfig.class).toInstance(callbackConfig);
        bind(PersistenceProvider.class).toInstance(persistenceProvider);
    }
}
