/*
 * Copyright 2016 Phaneesh Nagaraja <phaneesh.n@gmail.com>.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package callback;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.platform.callback.common.config.AppConfig;
import com.platform.callback.common.config.CallbackConfig;
import com.platform.callback.common.config.CallbackPathConfig;
import com.platform.callback.common.executor.CallbackExecutor;
import com.platform.callback.common.executor.CallbackExecutorFactory;
import com.platform.callback.common.guice.ExecutorInjectorModule;
import com.platform.callback.common.handler.InlineCallbackHandler;
import com.platform.callback.common.utils.ConstantUtils;
import com.platform.callback.core.services.DownstreamResponseHandler;
import com.platform.callback.rmq.RMQActionMessagePublisher;
import com.platform.callback.rmq.actors.impl.MessageHandlingActor;
import com.platform.callback.rmq.actors.impl.RmqCallbackMessageHandlingActor;
import com.platform.callback.rmq.actors.messages.ActionMessage;
import io.dropwizard.Configuration;
import io.dropwizard.actors.actor.ActorConfig;
import io.dropwizard.actors.connectivity.RMQConnection;
import io.dropwizard.jersey.DropwizardResourceConfig;
import io.dropwizard.jersey.setup.JerseyEnvironment;
import io.dropwizard.jetty.MutableServletContextHandler;
import io.dropwizard.lifecycle.setup.LifecycleEnvironment;
import io.dropwizard.revolver.RevolverBundle;
import io.dropwizard.revolver.core.config.RevolverConfig;
import io.dropwizard.revolver.handler.ConfigSource;
import io.dropwizard.revolver.persistence.InMemoryPersistenceProvider;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.junit.Before;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;

/**
 * @author nitishgoyal13
 */
@Slf4j
public abstract class BaseCallbackTest {

    protected static final InMemoryPersistenceProvider inMemoryPersistenceProvider = new InMemoryPersistenceProvider();
    protected static final ObjectMapper mapper = new ObjectMapper();
    protected static InlineCallbackHandler callbackHandler;
    protected static DownstreamResponseHandler downstreamResponseHandler;

    static final Environment environment = mock(Environment.class);
    static final CallbackConfig callbackConfig;
    static final AppConfig appConfig;

    private static RevolverConfig revolverConfig;


    static {
        revolverConfig = ConstantUtils.getRevolverConfig();
        callbackHandler = InlineCallbackHandler.builder()
                .persistenceProvider(inMemoryPersistenceProvider)
                .revolverConfig(revolverConfig)
                .build();

        callbackConfig = CallbackConfig.builder()
                .callbackType(CallbackConfig.CallbackType.INLINE)
                .build();

        appConfig = AppConfig.builder()
                .revolver(revolverConfig)
                .callbackConfig(callbackConfig)
                .build();

        Injector injector = Guice.createInjector(new ExecutorInjectorModule(callbackHandler, callbackConfig, inMemoryPersistenceProvider));
        CallbackExecutorFactory callbackExecutorFactory = new CallbackExecutorFactory(injector);
        CallbackExecutor callbackExecutor = callbackExecutorFactory.getExecutor(callbackConfig.getCallbackType());
        callbackExecutor.initialize(appConfig, environment);

        downstreamResponseHandler = DownstreamResponseHandler.builder()
                .callbackHandler(callbackHandler)
                .persistenceProvider(inMemoryPersistenceProvider)
                .callbackExecutor(callbackExecutor)
                .build();
    }

    final RevolverBundle<Configuration> bundle = new RevolverBundle<Configuration>() {

        @Override
        public RevolverConfig getRevolverConfig(final Configuration configuration) {
            return revolverConfig;
        }

        public String getRevolverConfigAttribute() { return "revolver"; }

        @Override
        public CuratorFramework getCurator() {
            return null;
        }

        @Override
        public ConfigSource getConfigSource() {
            return null;
        }
    };
    final Configuration configuration = mock(Configuration.class);
    private final Bootstrap<?> bootstrap = mock(Bootstrap.class);
    private final HealthCheckRegistry healthChecks = mock(HealthCheckRegistry.class);
    private final JerseyEnvironment jerseyEnvironment = mock(JerseyEnvironment.class);
    private final LifecycleEnvironment lifecycleEnvironment = new LifecycleEnvironment();
    private final RMQConnection rmqConnection = mock(RMQConnection.class);
    private MetricRegistry metricRegistry = new MetricRegistry();


    @Before
    public void setup() throws Exception {
        when(jerseyEnvironment.getResourceConfig()).thenReturn(new DropwizardResourceConfig());
        when(environment.jersey()).thenReturn(jerseyEnvironment);
        when(environment.lifecycle()).thenReturn(lifecycleEnvironment);
        when(environment.healthChecks()).thenReturn(healthChecks);
        when(environment.getObjectMapper()).thenReturn(mapper);
        when(bootstrap.getObjectMapper()).thenReturn(mapper);
        when(environment.metrics()).thenReturn(metricRegistry);
        when(environment.getApplicationContext()).thenReturn(new MutableServletContextHandler());

        bundle.initialize(bootstrap);
        bundle.run(configuration, environment);

        lifecycleEnvironment.getManagedObjects()
                .forEach(object -> {
                    try {
                        object.start();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });


        List<MessageHandlingActor> messageHandlingActorList = Lists.newArrayList();

        Map<String, ActorConfig> actorConfigMap = Maps.newHashMap();
        ActorConfig actorConfig = ActorConfig.builder()
                .concurrency(2)
                .delayed(false)
                .build();
        actorConfigMap.put(ActionMessage.DEFAULT_QUEUE_ID, actorConfig);

        updateCallbackConfig(callbackConfig, actorConfigMap);

        MessageHandlingActor messageHandlingActor = Mockito.spy(
                new RmqCallbackMessageHandlingActor(ActionMessage.DEFAULT_QUEUE_ID, actorConfig, rmqConnection,
                                                    environment.getObjectMapper(), callbackHandler, inMemoryPersistenceProvider
                ));
        doNothing().when(messageHandlingActor)
                .publish(ArgumentMatchers.any(ActionMessage.class));
        messageHandlingActorList.add(messageHandlingActor);
        RMQActionMessagePublisher.initialize(messageHandlingActorList);
    }

    private void updateCallbackConfig(CallbackConfig callbackConfig, Map<String, ActorConfig> actorConfigMap) {

        List<CallbackPathConfig> callbackPathConfigs = Lists.newArrayList();
        callbackPathConfigs.add(CallbackPathConfig.builder()
                                        .pathIds(Lists.newArrayList("/apis/test/v1/test/*", ""))
                                        .queueId(ActionMessage.DEFAULT_QUEUE_ID)
                                        .build());
        callbackConfig.setActors(actorConfigMap);
        callbackConfig.setCallbackPathConfigs(callbackPathConfigs);
        callbackConfig.setCallbackType(CallbackConfig.CallbackType.RMQ);
    }
}
