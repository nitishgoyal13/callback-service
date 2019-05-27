package com.platform.callback.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.platform.callback.executor.CallbackExecutorFactory;

/***
 Created by nitish.goyal on 27/05/19
 ***/
public class ExecutorInjectorModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(CallbackExecutorFactory.class).in(Scopes.SINGLETON);
    }
}
