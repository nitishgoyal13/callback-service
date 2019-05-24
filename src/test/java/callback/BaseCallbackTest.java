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
import com.platform.callback.App;
import com.platform.callback.handler.InlineCallbackHandler;
import com.platform.callback.config.CallbackConfig;
import com.platform.callback.config.CallbackPathConfig;
import com.platform.callback.rabbitmq.RMQActionMessagePublisher;
import com.platform.callback.rabbitmq.actors.impl.RmqCallbackMessageHandlingActor;
import com.platform.callback.rabbitmq.actors.impl.MessageHandlingActor;
import com.platform.callback.rabbitmq.actors.messages.ActionMessage;
import com.platform.callback.services.DownstreamResponseHandler;
import io.dropwizard.Configuration;
import io.dropwizard.actors.actor.ActorConfig;
import io.dropwizard.actors.connectivity.RMQConnection;
import io.dropwizard.jersey.DropwizardResourceConfig;
import io.dropwizard.jersey.setup.JerseyEnvironment;
import io.dropwizard.jetty.MutableServletContextHandler;
import io.dropwizard.lifecycle.setup.LifecycleEnvironment;
import io.dropwizard.revolver.RevolverBundle;
import io.dropwizard.revolver.core.config.*;
import io.dropwizard.revolver.core.config.hystrix.ThreadPoolConfig;
import io.dropwizard.revolver.discovery.ServiceResolverConfig;
import io.dropwizard.revolver.discovery.model.SimpleEndpointSpec;
import io.dropwizard.revolver.handler.ConfigSource;
import io.dropwizard.revolver.http.config.RevolverHttpApiConfig;
import io.dropwizard.revolver.http.config.RevolverHttpServiceConfig;
import io.dropwizard.revolver.http.config.RevolverHttpsServiceConfig;
import io.dropwizard.revolver.persistence.InMemoryPersistenceProvider;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
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
public class BaseCallbackTest {

    protected static final InMemoryPersistenceProvider inMemoryPersistenceProvider = new InMemoryPersistenceProvider();
    protected static final ObjectMapper mapper = new ObjectMapper();
    protected static InlineCallbackHandler callbackHandler;
    protected static DownstreamResponseHandler downstreamResponseHandler;

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

    private static final Environment environment = mock(Environment.class);
    private final Bootstrap<?> bootstrap = mock(Bootstrap.class);
    private final HealthCheckRegistry healthChecks = mock(HealthCheckRegistry.class);
    private final JerseyEnvironment jerseyEnvironment = mock(JerseyEnvironment.class);
    private final LifecycleEnvironment lifecycleEnvironment = new LifecycleEnvironment();
    private final RMQConnection rmqConnection = mock(RMQConnection.class);


    private MetricRegistry metricRegistry = new MetricRegistry();
    private static RevolverConfig revolverConfig;

    static {
        revolverConfig = getRevolverConfig();
        callbackHandler = InlineCallbackHandler.builder()
                .persistenceProvider(inMemoryPersistenceProvider)
                .revolverConfig(revolverConfig)
                .build();
        downstreamResponseHandler = DownstreamResponseHandler.builder()
                .callbackHandler(callbackHandler)
                .persistenceProvider(inMemoryPersistenceProvider)
                .build();
    }

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


        CallbackConfig callbackConfig = getCallbackConfig(actorConfigMap);
        App.initializeMeta(callbackConfig);

        MessageHandlingActor messageHandlingActor = Mockito.spy(
                new RmqCallbackMessageHandlingActor(ActionMessage.DEFAULT_QUEUE_ID, actorConfig, rmqConnection, environment.getObjectMapper(),
                                                    callbackHandler, inMemoryPersistenceProvider
                ));
        doNothing().when(messageHandlingActor)
                .publish(ArgumentMatchers.any(ActionMessage.class));
        messageHandlingActorList.add(messageHandlingActor);
        RMQActionMessagePublisher.initialize(messageHandlingActorList);
    }

    private CallbackConfig getCallbackConfig(Map<String, ActorConfig> actorConfigMap) {

        List<CallbackPathConfig> callbackPathConfigs = Lists.newArrayList();
        callbackPathConfigs.add(CallbackPathConfig.builder()
                                        .pathIds(Lists.newArrayList("/apis/test/v1/test/*", ""))
                                        .queueId(ActionMessage.DEFAULT_QUEUE_ID)
                                        .build());
        return CallbackConfig.builder()
                .actors(actorConfigMap)
                .callbackType(CallbackConfig.CallbackType.RMQ)
                .callbackPathConfigs(callbackPathConfigs)
                .build();
    }

    private static RevolverConfig getRevolverConfig() {

        val simpleEndpoint = new SimpleEndpointSpec();
        simpleEndpoint.setHost("localhost");
        simpleEndpoint.setPort(9999);

        val securedEndpoint = new SimpleEndpointSpec();
        securedEndpoint.setHost("localhost");
        securedEndpoint.setPort(9933);

        ThreadPoolConfig threadPoolConfig = ThreadPoolConfig.builder()
                .concurrency(2)
                .dynamicRequestQueueSize(2)
                .threadPoolName("test")
                .timeout(100)
                .build();


        return RevolverConfig.builder()
                .mailBox(InMemoryMailBoxConfig.builder()
                                 .build())
                .serviceResolverConfig(ServiceResolverConfig.builder()
                                               .namespace("test")
                                               .useCurator(false)
                                               .zkConnectionString("localhost:2181")
                                               .build())
                .clientConfig(ClientConfig.builder()
                                      .clientName("test-client")
                                      .build())
                .global(new RuntimeConfig())
                .service(RevolverHttpServiceConfig.builder()
                                 .authEnabled(false)
                                 .connectionPoolSize(1)
                                 .secured(false)
                                 .enpoint(simpleEndpoint)
                                 .service("test")
                                 .type("http")
                                 .threadPoolGroupConfig(ThreadPoolGroupConfig.builder()
                                                                .threadPools(Lists.newArrayList(threadPoolConfig))
                                                                .build())
                                 .api(RevolverHttpApiConfig.configBuilder()
                                              .api("test")
                                              .method(RevolverHttpApiConfig.RequestMethod.GET)
                                              .method(RevolverHttpApiConfig.RequestMethod.POST)
                                              .method(RevolverHttpApiConfig.RequestMethod.DELETE)
                                              .method(RevolverHttpApiConfig.RequestMethod.PATCH)
                                              .method(RevolverHttpApiConfig.RequestMethod.PUT)
                                              .method(RevolverHttpApiConfig.RequestMethod.HEAD)
                                              .method(RevolverHttpApiConfig.RequestMethod.OPTIONS)
                                              .path("{version}/test")
                                              .runtime(HystrixCommandConfig.builder()
                                                               .threadPool(ThreadPoolConfig.builder()
                                                                                   .concurrency(1)
                                                                                   .timeout(2000)
                                                                                   .build())
                                                               .build())
                                              .build())
                                 .api(RevolverHttpApiConfig.configBuilder()
                                              .api("testAsync")
                                              .method(RevolverHttpApiConfig.RequestMethod.GET)
                                              .method(RevolverHttpApiConfig.RequestMethod.POST)
                                              .method(RevolverHttpApiConfig.RequestMethod.DELETE)
                                              .method(RevolverHttpApiConfig.RequestMethod.PATCH)
                                              .method(RevolverHttpApiConfig.RequestMethod.PUT)
                                              .method(RevolverHttpApiConfig.RequestMethod.HEAD)
                                              .method(RevolverHttpApiConfig.RequestMethod.OPTIONS)
                                              .path("{version}/test/async")
                                              .runtime(HystrixCommandConfig.builder()
                                                               .threadPool(ThreadPoolConfig.builder()
                                                                                   .concurrency(1)
                                                                                   .timeout(2000)
                                                                                   .build())
                                                               .build())
                                              .build())
                                 .api(RevolverHttpApiConfig.configBuilder()
                                              .api("test_multi")
                                              .method(RevolverHttpApiConfig.RequestMethod.GET)
                                              .method(RevolverHttpApiConfig.RequestMethod.POST)
                                              .method(RevolverHttpApiConfig.RequestMethod.DELETE)
                                              .method(RevolverHttpApiConfig.RequestMethod.PATCH)
                                              .method(RevolverHttpApiConfig.RequestMethod.PUT)
                                              .method(RevolverHttpApiConfig.RequestMethod.HEAD)
                                              .method(RevolverHttpApiConfig.RequestMethod.OPTIONS)
                                              .path("{version}/test/{operation}")
                                              .runtime(HystrixCommandConfig.builder()
                                                               .threadPool(ThreadPoolConfig.builder()
                                                                                   .concurrency(1)
                                                                                   .timeout(2000)
                                                                                   .build())
                                                               .build())
                                              .build())
                                 .api(RevolverHttpApiConfig.configBuilder()
                                              .api("callback")
                                              .method(RevolverHttpApiConfig.RequestMethod.GET)
                                              .method(RevolverHttpApiConfig.RequestMethod.POST)
                                              .method(RevolverHttpApiConfig.RequestMethod.DELETE)
                                              .method(RevolverHttpApiConfig.RequestMethod.PATCH)
                                              .method(RevolverHttpApiConfig.RequestMethod.PUT)
                                              .method(RevolverHttpApiConfig.RequestMethod.HEAD)
                                              .method(RevolverHttpApiConfig.RequestMethod.OPTIONS)
                                              .path("{version}/test/callback")
                                              .runtime(HystrixCommandConfig.builder()
                                                               .threadPool(ThreadPoolConfig.builder()
                                                                                   .concurrency(1)
                                                                                   .timeout(2000)
                                                                                   .build())
                                                               .build())
                                              .build())
                                 .api(RevolverHttpApiConfig.configBuilder()
                                              .api("test_group_thread_pool")
                                              .method(RevolverHttpApiConfig.RequestMethod.GET)
                                              .method(RevolverHttpApiConfig.RequestMethod.POST)
                                              .method(RevolverHttpApiConfig.RequestMethod.DELETE)
                                              .method(RevolverHttpApiConfig.RequestMethod.PATCH)
                                              .method(RevolverHttpApiConfig.RequestMethod.PUT)
                                              .method(RevolverHttpApiConfig.RequestMethod.HEAD)
                                              .method(RevolverHttpApiConfig.RequestMethod.OPTIONS)
                                              .path("{version}/test/{operation}")
                                              .runtime(HystrixCommandConfig.builder()
                                                               .threadPool(ThreadPoolConfig.builder()
                                                                                   .concurrency(1)
                                                                                   .timeout(2000)
                                                                                   .build())
                                                               .build())
                                              .build())
                                 .build())
                .service(RevolverHttpsServiceConfig.builder()
                                 .authEnabled(false)
                                 .connectionPoolSize(1)
                                 .enpoint(securedEndpoint)
                                 .service("test_secured")
                                 .type("https")
                                 .api(RevolverHttpApiConfig.configBuilder()
                                              .api("test")
                                              .method(RevolverHttpApiConfig.RequestMethod.GET)
                                              .method(RevolverHttpApiConfig.RequestMethod.POST)
                                              .method(RevolverHttpApiConfig.RequestMethod.DELETE)
                                              .method(RevolverHttpApiConfig.RequestMethod.PATCH)
                                              .method(RevolverHttpApiConfig.RequestMethod.PUT)
                                              .method(RevolverHttpApiConfig.RequestMethod.HEAD)
                                              .method(RevolverHttpApiConfig.RequestMethod.OPTIONS)
                                              .path("{version}/test")
                                              .runtime(HystrixCommandConfig.builder()
                                                               .threadPool(ThreadPoolConfig.builder()
                                                                                   .concurrency(1)
                                                                                   .timeout(2000)
                                                                                   .build())
                                                               .build())
                                              .build())
                                 .api(RevolverHttpApiConfig.configBuilder()
                                              .api("test_multi")
                                              .method(RevolverHttpApiConfig.RequestMethod.GET)
                                              .method(RevolverHttpApiConfig.RequestMethod.POST)
                                              .method(RevolverHttpApiConfig.RequestMethod.DELETE)
                                              .method(RevolverHttpApiConfig.RequestMethod.PATCH)
                                              .method(RevolverHttpApiConfig.RequestMethod.PUT)
                                              .method(RevolverHttpApiConfig.RequestMethod.HEAD)
                                              .method(RevolverHttpApiConfig.RequestMethod.OPTIONS)
                                              .path("{version}/test/{operation}")
                                              .runtime(HystrixCommandConfig.builder()
                                                               .threadPool(ThreadPoolConfig.builder()
                                                                                   .concurrency(1)
                                                                                   .timeout(2000)
                                                                                   .build())
                                                               .build())
                                              .build())
                                 .build())
                .build();
    }
}
