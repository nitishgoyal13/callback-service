package com.platform.callback.annotations;

import com.platform.callback.config.CallbackConfig;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/***
 Created by nitish.goyal on 27/05/19
 ***/
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Executor {

    CallbackConfig.CallbackType callbackType();
}
