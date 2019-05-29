package com.platform.callback.common.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.platform.callback.common.executor.CallbackExecutorFactory;

/***
 Created by nitish.goyal on 27/05/19
 ***/
public class ExecutorInjectorModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(CallbackExecutorFactory.class).in(Scopes.SINGLETON);
    }
}
