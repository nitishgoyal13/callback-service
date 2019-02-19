package com.platform.callback.hystrix;

import io.appform.core.hystrix.CommandFactory;
import io.appform.core.hystrix.HandlerAdapter;
/***
 Created by nitish.goyal on 02/02/19
 ***/
public class HystrixUtil {

    public static <T> T execute(String group, String command, final HandlerAdapter<T> function) throws Exception {
        return ConfigManager.isHystrixEnabled() ? CommandFactory.<T>create(group, command).executor(function)
                .execute() : function.run();
    }

    public static <T> T execute(String group, String command, String traceId, final HandlerAdapter<T> function) throws Exception {
        return ConfigManager.isHystrixEnabled() ? CommandFactory.<T>create(group, command, traceId).executor(function)
                .execute() : function.run();
    }

}
