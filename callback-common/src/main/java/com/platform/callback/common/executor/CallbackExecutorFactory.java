package com.platform.callback.common.executor;

import com.collections.CollectionUtils;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.platform.callback.common.annotations.Executor;
import com.platform.callback.common.config.CallbackConfig;
import com.platform.callback.common.utils.ConstantUtils;
import lombok.Data;
import org.reflections.Reflections;

import java.util.Map;
import java.util.Set;

/***
 Created by nitish.goyal on 27/05/19
 ***/
@Data
public class CallbackExecutorFactory {

    private static Map<CallbackConfig.CallbackType, CallbackExecutor> factoryMap = Maps.newHashMap();

    @Inject
    public CallbackExecutorFactory(Injector injector) {
        Reflections reflections = new Reflections(ConstantUtils.BASE_PACKAGE);
        final Set<Class<?>> annotatedClasses = reflections.getTypesAnnotatedWith(Executor.class);

        annotatedClasses.forEach(annotatedType -> {
            if(CallbackExecutor.class.isAssignableFrom(annotatedType)) {
                final CallbackExecutor instance = CallbackExecutor.class.cast(injector.getInstance(annotatedType));
                factoryMap.put(instance.getType(), instance);
            }
        });
    }

    public CallbackExecutor getExecutor(CallbackConfig.CallbackType callbackType) {
        if(CollectionUtils.isEmpty(factoryMap) || factoryMap.get(callbackType) == null) {
            throw new RuntimeException("No mapper exists");
        }
        return factoryMap.get(callbackType);
    }
}
