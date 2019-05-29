package com.platform.callback;

import com.codahale.metrics.MetricRegistry;
import com.collections.CollectionUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.hystrix.configurator.core.HystrixConfigurationFactory;
import com.phonepe.rosey.dwconfig.RoseyConfigSourceProvider;
import com.platform.callback.config.AppConfig;
import com.platform.callback.config.CallbackConfig;
import com.platform.callback.handler.InlineCallbackHandler;
import com.platform.callback.rabbitmq.RMQActionMessagePublisher;
import com.platform.callback.rabbitmq.RMQWrapper;
import com.platform.callback.rabbitmq.actors.impl.MessageHandlingActor;
import com.platform.callback.rabbitmq.actors.impl.RmqCallbackMessageHandlingActor;
import com.platform.callback.rabbitmq.actors.messages.ActionMessage;
import com.platform.callback.resources.CallbackRequestResource;
import com.platform.callback.resources.CallbackResource;
import com.platform.callback.services.DownstreamResponseHandler;
import com.utils.StringUtils;
import io.dropwizard.Application;
import io.dropwizard.actors.RabbitmqActorBundle;
import io.dropwizard.actors.actor.ActorConfig;
import io.dropwizard.actors.config.RMQConfig;
import io.dropwizard.actors.connectivity.RMQConnection;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.discovery.bundle.ServiceDiscoveryBundle;
import io.dropwizard.discovery.bundle.ServiceDiscoveryConfiguration;
import io.dropwizard.oor.OorBundle;
import io.dropwizard.primer.PrimerBundle;
import io.dropwizard.primer.model.PrimerAuthorization;
import io.dropwizard.primer.model.PrimerAuthorizationMatrix;
import io.dropwizard.primer.model.PrimerBundleConfiguration;
import io.dropwizard.revolver.RevolverBundle;
import io.dropwizard.revolver.aeroapike.AerospikeConnectionManager;
import io.dropwizard.revolver.core.config.AerospikeMailBoxConfig;
import io.dropwizard.revolver.core.config.RevolverConfig;
import io.dropwizard.revolver.filters.RevolverRequestFilter;
import io.dropwizard.revolver.handler.ConfigSource;
import io.dropwizard.revolver.http.config.RevolverHttpApiConfig;
import io.dropwizard.revolver.http.config.RevolverHttpServiceConfig;
import io.dropwizard.revolver.persistence.AeroSpikePersistenceProvider;
import io.dropwizard.revolver.persistence.InMemoryPersistenceProvider;
import io.dropwizard.revolver.persistence.PersistenceProvider;
import io.dropwizard.riemann.RiemannBundle;
import io.dropwizard.riemann.RiemannConfig;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.curator.framework.CuratorFramework;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Created by nitishgoyal13 on 31/1/19.
 */
@Slf4j
public class App extends Application<AppConfig> {


    private static Map<String, String> pathVsQueueId = Maps.newHashMap();
    private static Map<String, CallbackConfig.CallbackType> pathVsCallbackType = Maps.newHashMap();

    @Override
    public void initialize(Bootstrap<AppConfig> bootstrap) {
        bootstrap.addBundle(new OorBundle<AppConfig>() {
            @Override
            public boolean withOor() {
                return false;
            }
        });
        String localConfigStr = System.getenv("localConfig");
        RoseyConfigSourceProvider roseyConfigSourceProvider = new RoseyConfigSourceProvider("edge", "apicallback");

        boolean localConfig = !Strings.isNullOrEmpty(localConfigStr) && Boolean.parseBoolean(localConfigStr);
        if(localConfig) {
            bootstrap.setConfigurationSourceProvider(
                    new SubstitutingSourceProvider(bootstrap.getConfigurationSourceProvider(), new EnvironmentVariableSubstitutor()));
        } else {
            bootstrap.setConfigurationSourceProvider(roseyConfigSourceProvider);
        }

        ServiceDiscoveryBundle<AppConfig> serviceDiscoveryBundle = new ServiceDiscoveryBundle<AppConfig>() {
            @Override
            protected ServiceDiscoveryConfiguration getRangerConfiguration(AppConfig configuration) {
                return configuration.getDiscovery();
            }

            @Override
            protected String getServiceName(AppConfig configuration) {
                return configuration.getAppName();
            }

            @Override
            protected int getPort(AppConfig configuration) {
                return configuration.getDiscovery()
                        .getPublishedPort();
            }
        };
        bootstrap.addBundle(serviceDiscoveryBundle);


        bootstrap.addBundle(new RevolverBundle<AppConfig>() {

            @Override
            public CuratorFramework getCurator() {
                return serviceDiscoveryBundle.getCurator();
            }

            @Override
            public io.dropwizard.revolver.core.config.RevolverConfig getRevolverConfig(AppConfig appConfig) {
                return appConfig.getRevolver();
            }

            @Override
            public String getRevolverConfigAttribute() {
                return "revolver";
            }

            @Override
            public ConfigSource getConfigSource() {
                return () -> {
                    try {
                        return roseyConfigSourceProvider.fetchRemoteConfig();
                    } catch (Exception e) {
                        log.error("Exception in getting source ", e);

                    }
                    return null;
                };

            }
        });

        bootstrap.addBundle(new PrimerBundle<AppConfig>() {

            @Override
            public CuratorFramework getCurator(AppConfig configuration) {
                return serviceDiscoveryBundle.getCurator();
            }

            @Override
            public PrimerBundleConfiguration getPrimerConfiguration(AppConfig apiConfiguration) {
                return apiConfiguration.getPrimer();
            }

            @Override
            public Set<String> withWhiteList(AppConfig apiConfiguration) {
                return apiConfiguration.getRevolver()
                        .getServices()
                        .stream()
                        .filter(service -> service instanceof RevolverHttpServiceConfig)
                        .map(service -> ((RevolverHttpServiceConfig)service).getApis()
                                .stream()
                                .filter(RevolverHttpApiConfig::isWhitelist)
                                .map(a -> "apis/" + service.getService() + "/" + a.getPath())
                                .collect(Collectors.toSet()))
                        .flatMap(Collection::stream)
                        .collect(Collectors.toSet());
            }

            @Override
            public PrimerAuthorizationMatrix withAuthorization(AppConfig apiConfiguration) {
                val staticAuth = apiConfiguration.getRevolver()
                        .getServices()
                        .stream()
                        .filter(service -> service instanceof RevolverHttpServiceConfig)
                        .map(service -> ((RevolverHttpServiceConfig)service).getApis()
                                .stream()
                                .filter(a -> !a.isWhitelist())
                                .filter(this::checkStaticAuthorization)
                                .map(a -> primerStaticAuthorization(a, (RevolverHttpServiceConfig)service))
                                .collect(Collectors.toSet()))
                        .flatMap(Collection::stream)
                        .collect(Collectors.toList());
                val dynamicAuth = apiConfiguration.getRevolver()
                        .getServices()
                        .stream()
                        .filter(service -> service instanceof RevolverHttpServiceConfig)
                        .map(service -> ((RevolverHttpServiceConfig)service).getApis()
                                .stream()
                                .filter(a -> !a.isWhitelist())
                                .filter(this::checkDynamicAuthorization)
                                .map(a -> primerDynamicAuthorization(a, ((RevolverHttpServiceConfig)service)))
                                .collect(Collectors.toSet()))
                        .flatMap(Collection::stream)
                        .collect(Collectors.toList());
                val autoAuth = apiConfiguration.getRevolver()
                        .getServices()
                        .stream()
                        .filter(service -> service instanceof RevolverHttpServiceConfig)
                        .map(service -> ((RevolverHttpServiceConfig)service).getApis()
                                .stream()
                                .filter(a -> !a.isWhitelist())
                                .filter(this::checkAutoAuthorization)
                                .map(a -> primerAutoAuthorization(a, ((RevolverHttpServiceConfig)service)))
                                .collect(Collectors.toSet()))
                        .flatMap(Collection::stream)
                        .collect(Collectors.toList());
                if(apiConfiguration.getPrimer() != null && apiConfiguration.getPrimer()
                                                                   .getAuthorizations() != null) {
                    if(apiConfiguration.getPrimer()
                               .getAuthorizations()
                               .getAutoAuthorizations() != null) {
                        autoAuth.addAll(apiConfiguration.getPrimer()
                                                .getAuthorizations()
                                                .getAutoAuthorizations());
                    }
                    if(apiConfiguration.getPrimer()
                               .getAuthorizations()
                               .getStaticAuthorizations() != null) {
                        autoAuth.addAll(apiConfiguration.getPrimer()
                                                .getAuthorizations()
                                                .getStaticAuthorizations());
                    }
                    if(apiConfiguration.getPrimer()
                               .getAuthorizations()
                               .getAuthorizations() != null) {
                        dynamicAuth.addAll(apiConfiguration.getPrimer()
                                                   .getAuthorizations()
                                                   .getAuthorizations());
                    }
                }
                return PrimerAuthorizationMatrix.builder()
                        .staticAuthorizations(staticAuth)
                        .authorizations(dynamicAuth)
                        .autoAuthorizations(autoAuth)
                        .build();
            }

            private PrimerAuthorization primerDynamicAuthorization(RevolverHttpApiConfig apiConfig,
                                                                   RevolverHttpServiceConfig serviceConfig) {
                return PrimerAuthorization.builder()
                        .type("dynamic")
                        .url("apis/" + serviceConfig.getService() + "/" + apiConfig.getPath())
                        .methods(apiConfig.getAuthorization()
                                         .getMethods())
                        .roles(apiConfig.getAuthorization()
                                       .getRoles())
                        .build();
            }

            private boolean checkDynamicAuthorization(RevolverHttpApiConfig apiConfig) {
                return (!apiConfig.isWhitelist() && apiConfig.getAuthorization() != null && apiConfig.getAuthorization()
                        .getType()
                        .equals("dynamic"));
            }

            private PrimerAuthorization primerStaticAuthorization(RevolverHttpApiConfig apiConfig,
                                                                  RevolverHttpServiceConfig serviceConfig) {
                return PrimerAuthorization.builder()
                        .type("static")
                        .url("apis/" + serviceConfig.getService() + "/" + apiConfig.getPath())
                        .methods(apiConfig.getAuthorization()
                                         .getMethods())
                        .roles(apiConfig.getAuthorization()
                                       .getRoles())
                        .build();
            }

            private boolean checkStaticAuthorization(RevolverHttpApiConfig apiConfig) {
                return (!apiConfig.isWhitelist() && apiConfig.getAuthorization() != null && apiConfig.getAuthorization()
                        .getType()
                        .equals("static"));
            }

            private PrimerAuthorization primerAutoAuthorization(RevolverHttpApiConfig apiConfig, RevolverHttpServiceConfig serviceConfig) {
                return PrimerAuthorization.builder()
                        .type("auto")
                        .url("apis/" + serviceConfig.getService() + "/" + apiConfig.getPath())
                        .methods(apiConfig.getAuthorization()
                                         .getMethods())
                        .roles(apiConfig.getAuthorization()
                                       .getRoles())
                        .build();
            }

            private boolean checkAutoAuthorization(RevolverHttpApiConfig apiConfig) {
                return (!apiConfig.isWhitelist() && apiConfig.getAuthorization() != null && apiConfig.getAuthorization()
                        .getType()
                        .equals("auto"));
            }

        });

        RabbitmqActorBundle<AppConfig> rabbitmqActorBundle = new RabbitmqActorBundle<AppConfig>() {
            @Override
            protected RMQConfig getConfig(AppConfig appConfig) {
                return appConfig.getRmqConfig();
            }
        };
        bootstrap.addBundle(rabbitmqActorBundle);

        bootstrap.addBundle(new RiemannBundle<AppConfig>() {

            @Override
            public RiemannConfig getRiemannConfiguration(AppConfig configuration) {
                return configuration.getRiemann();
            }
        });

        /*bootstrap.addBundle(GuiceBundle.<AppConfig>builder().enableAutoConfig(packageNameList.toArray(new String[0]))
                                    .modules(new ExecutorInjectorModule())
                                    .build(Stage.PRODUCTION));*/


    }

    @Override
    public void run(AppConfig configuration, Environment environment) {
        val objectMapper = environment.getObjectMapper();
        val metrics = environment.metrics();

        log.info("InitializedManagedObject type: rabbitMqConnection");

        HystrixConfigurationFactory.init(configuration.getHystrixConfig());

        final PersistenceProvider persistenceProvider = getPersistenceProvider(configuration, environment);
        final InlineCallbackHandler callbackHandler = InlineCallbackHandler.builder()
                .persistenceProvider(persistenceProvider)
                .revolverConfig(configuration.getRevolver())
                .build();
        environment.jersey()
                .register(new RevolverRequestFilter(configuration.getRevolver()));


        CallbackConfig callbackConfig = configuration.getCallbackConfig();
        initializeMeta(callbackConfig);

        if(CallbackConfig.CallbackType.RMQ.equals(callbackConfig.getCallbackType())) {
            setupRmq(configuration, environment, metrics, objectMapper, callbackHandler, persistenceProvider);
        }

        DownstreamResponseHandler downstreamResponseHandler = DownstreamResponseHandler.builder()
                .persistenceProvider(persistenceProvider)
                .callbackHandler(callbackHandler)
                .build();

        CallbackRequestResource callbackRequestResource = CallbackRequestResource.builder()
                .callbackHandler(callbackHandler)
                .jsonObjectMapper(objectMapper)
                .persistenceProvider(persistenceProvider)
                .msgPackObjectMapper(objectMapper)
                .downstreamResponseHandler(downstreamResponseHandler)
                .build();

        CallbackResource callbackResource = CallbackResource.builder()
                .callbackHandler(callbackHandler)
                .persistenceProvider(persistenceProvider)
                .downstreamResponseHandler(downstreamResponseHandler)
                .build();

        environment.jersey()
                .register(callbackRequestResource);
        environment.jersey()
                .register(callbackResource);

    }

    private void setupRmq(AppConfig configuration, Environment environment, MetricRegistry metrics, ObjectMapper objectMapper,
                          InlineCallbackHandler callbackHandler, PersistenceProvider persistenceProvider) {

        List<MessageHandlingActor> rmqMessageHandlingActors = Lists.newArrayList();
        CallbackConfig callbackConfig = configuration.getCallbackConfig();
        RMQConnection rmqConnection = initializeRmqConnection(configuration, metrics);
        Map<String, ActorConfig> actors = callbackConfig.getActors();
        actors.forEach((a, actorConfig) -> rmqMessageHandlingActors.add(
                new RmqCallbackMessageHandlingActor(a, actorConfig, rmqConnection, objectMapper, callbackHandler, persistenceProvider)));

        RMQActionMessagePublisher.initialize(rmqMessageHandlingActors);

        environment.lifecycle()
                .manage(new RMQWrapper(rmqConnection));
    }

    private RMQConnection initializeRmqConnection(AppConfig configuration, MetricRegistry metrics) {

        Map<String, ActorConfig> actors = configuration.getCallbackConfig()
                .getActors();

        AtomicInteger rmqConcurrency = new AtomicInteger();
        actors.forEach((a, actorConfig) -> rmqConcurrency.addAndGet(actorConfig.getConcurrency()));


        return new RMQConnection(configuration.getRmqConfig(), metrics, Executors.newFixedThreadPool(rmqConcurrency.get()));
    }

    public static CallbackConfig.CallbackType getCallbackType(String path) {

        if(StringUtils.isEmpty(path)) {
            return CallbackConfig.CallbackType.INLINE;
        }

        final CallbackConfig.CallbackType[] toReturn = new CallbackConfig.CallbackType[1];
        pathVsCallbackType.forEach((s, callbackType) -> {
            Pattern pattern = Pattern.compile(s);
            log.info("Regex : " + s + ", matcher : " + path);
            if(pattern.matcher(path)
                    .find()) {
                toReturn[0] = callbackType;
            }
        });
        if(toReturn[0] == null) {
            return CallbackConfig.CallbackType.INLINE;
        }
        return toReturn[0];
    }

    public static String getQueueId(String path) {

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

    public static void initializeMeta(CallbackConfig callbackConfig) {

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


    private PersistenceProvider getPersistenceProvider(final AppConfig configuration, final Environment environment) {
        final RevolverConfig revolverConfig = configuration.getRevolver();
        //Default for avoiding no mailbox config NPE
        if(revolverConfig.getMailBox() == null) {
            return new InMemoryPersistenceProvider();
        }
        switch (revolverConfig.getMailBox()
                .getType()) {
            case "in_memory":
                return new InMemoryPersistenceProvider();
            case "aerospike":
                AerospikeConnectionManager.init((AerospikeMailBoxConfig)revolverConfig.getMailBox());
                return new AeroSpikePersistenceProvider((AerospikeMailBoxConfig)revolverConfig.getMailBox(), environment.getObjectMapper());
            default:
                throw new IllegalArgumentException("Invalid mailbox configuration");
        }
    }

}
