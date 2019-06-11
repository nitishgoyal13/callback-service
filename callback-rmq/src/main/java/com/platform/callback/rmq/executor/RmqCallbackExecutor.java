package com.platform.callback.rmq.executor;

import com.codahale.metrics.MetricRegistry;
import com.collections.CollectionUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import com.platform.callback.common.annotations.Executor;
import com.platform.callback.common.config.AppConfig;
import com.platform.callback.common.config.CallbackConfig;
import com.platform.callback.common.exception.CallbackException;
import com.platform.callback.common.executor.CallbackExecutor;
import com.platform.callback.common.handler.CallbackHandler;
import com.platform.callback.rmq.RMQActionMessagePublisher;
import com.platform.callback.rmq.RMQWrapper;
import com.platform.callback.rmq.actors.impl.MessageHandlingActor;
import com.platform.callback.rmq.actors.impl.RmqCallbackMessageHandlingActor;
import com.platform.callback.rmq.actors.messages.ActionMessage;
import com.platform.callback.rmq.actors.messages.CallbackMessage;
import com.utils.StringUtils;
import io.dropwizard.actors.actor.ActorConfig;
import io.dropwizard.actors.connectivity.RMQConnection;
import io.dropwizard.revolver.base.core.RevolverCallbackResponse;
import io.dropwizard.revolver.persistence.PersistenceProvider;
import io.dropwizard.setup.Environment;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/***
 Created by nitish.goyal on 29/05/19
 ***/
@Slf4j
@Executor(callbackType = CallbackConfig.CallbackType.RMQ)
public class RmqCallbackExecutor implements CallbackExecutor {

    private static Map<String, String> pathVsQueueId = Maps.newHashMap();
    private static Map<String, CallbackConfig.CallbackType> pathVsCallbackType = Maps.newHashMap();

    private PersistenceProvider persistenceProvider;
    private CallbackHandler callbackHandler;
    private CallbackConfig callbackConfig;


    @Builder
    @Inject
    public RmqCallbackExecutor(CallbackConfig callbackConfig, PersistenceProvider persistenceProvider, CallbackHandler callbackHandler) {
        initializeMeta(callbackConfig);
        this.persistenceProvider = persistenceProvider;
        this.callbackConfig = callbackConfig;
        this.callbackHandler = callbackHandler;
    }

    @Override
    public void initialize(AppConfig configuration, Environment environment) {
        List<MessageHandlingActor> rmqMessageHandlingActors = Lists.newArrayList();
        RMQConnection rmqConnection = initializeRmqConnection(configuration, environment.metrics());
        Map<String, ActorConfig> actors = callbackConfig.getActors();
        actors.forEach((a, actorConfig) -> rmqMessageHandlingActors.add(
                new RmqCallbackMessageHandlingActor(a, actorConfig, rmqConnection, environment.getObjectMapper(), callbackHandler,
                                                    persistenceProvider
                )));

        RMQActionMessagePublisher.initialize(rmqMessageHandlingActors);

        environment.lifecycle()
                .manage(new RMQWrapper(rmqConnection));
    }

    @Override
    public void execute(String requestId, RevolverCallbackResponse response, String path) {

        try {
            String queueId = getQueueId(path);
            if(StringUtils.isEmpty(queueId)) {
                throw new CallbackException(Response.Status.INTERNAL_SERVER_ERROR, "Queue not found for the path");
            }
            log.info("Inputting message to Queue with queueId : " + queueId);
            RMQActionMessagePublisher.publish(CallbackMessage.builder()
                                                      .requestId(requestId)
                                                      .type(CallbackConfig.CallbackType.RMQ.name())
                                                      .queueId(queueId)
                                                      .build());
        } catch (Exception e) {
            throw new CallbackException(Response.Status.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @Override
    public CallbackConfig.CallbackType getType() {
        return CallbackConfig.CallbackType.RMQ;
    }

    private static void initializeMeta(CallbackConfig callbackConfig) {

        if(callbackConfig == null || CollectionUtils.isEmpty(callbackConfig.getCallbackPathConfigs())) {
            return;
        }

        callbackConfig.getCallbackPathConfigs()
                .forEach(callbackPathConfig -> {
                    if(CallbackConfig.CallbackType.RMQ.equals(callbackConfig.getCallbackType())) {
                        callbackPathConfig.getPathIds()
                                .forEach(s -> pathVsQueueId.put(s, callbackPathConfig.getQueueId()));

                    }
                    callbackPathConfig.getPathIds()
                            .forEach(s -> callbackPathConfig.getPathIds()
                                    .forEach(path -> pathVsCallbackType.put(path, callbackConfig.getCallbackType())));


                });
    }

    private String getQueueId(String path) {

        if(StringUtils.isEmpty(path)) {
            return ActionMessage.DEFAULT_QUEUE_ID;
        }
        final String[] toReturn = new String[1];
        pathVsQueueId.forEach((s, queueId) -> {
            Pattern pattern = Pattern.compile(s);
            if(pattern.matcher(path)
                    .find()) {
                toReturn[0] = queueId;
                log.info("Queue Id : " + queueId);
            }
        });
        if(StringUtils.isEmpty(toReturn[0])) {
            return ActionMessage.DEFAULT_QUEUE_ID;
        }
        return toReturn[0];
    }

    private RMQConnection initializeRmqConnection(AppConfig configuration, MetricRegistry metrics) {

        Map<String, ActorConfig> actors = callbackConfig.getActors();

        AtomicInteger rmqConcurrency = new AtomicInteger();
        actors.forEach((a, actorConfig) -> rmqConcurrency.addAndGet(actorConfig.getConcurrency()));


        return new RMQConnection(configuration.getRmqConfig(), metrics, Executors.newFixedThreadPool(rmqConcurrency.get()));
    }

}
