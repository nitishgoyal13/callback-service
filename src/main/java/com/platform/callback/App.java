package com.platform.callback;

import com.collections.CollectionUtils;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.hystrix.configurator.core.HystrixConfigurationFactory;
import com.phonepe.rosey.dwconfig.RoseyConfigSourceProvider;
import com.platform.callback.rabbitmq.ActionMessagePublisher;
import com.platform.callback.rabbitmq.RMQWrapper;
import com.platform.callback.rabbitmq.actors.impl.CallbackMessageHandlingActor;
import com.platform.callback.rabbitmq.actors.impl.MessageHandlingActor;
import com.platform.callback.resources.CallbackRequestResource;
import io.dropwizard.Application;
import io.dropwizard.actors.RabbitmqActorBundle;
import io.dropwizard.actors.actor.ActorConfig;
import io.dropwizard.actors.config.RMQConfig;
import io.dropwizard.actors.connectivity.RMQConnection;
import io.dropwizard.checkmate.CheckmateBundle;
import io.dropwizard.checkmate.model.CheckmateBundleConfiguration;
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
import io.dropwizard.revolver.callback.InlineCallbackHandler;
import io.dropwizard.revolver.core.config.AerospikeMailBoxConfig;
import io.dropwizard.revolver.core.config.RevolverConfig;
import io.dropwizard.revolver.core.config.RevolverServiceConfig;
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
import java.util.stream.Collectors;

/**
 * Created by nitishgoyal13 on 31/1/19.
 */
@Slf4j
public class App extends Application<AppConfig> {

    private static RoseyConfigSourceProvider roseyConfigSourceProvider;

    @Override
    public void initialize(Bootstrap<AppConfig> bootstrap) {
        bootstrap.addBundle(new OorBundle<AppConfig>() {
            @Override
            public boolean withOor() {
                return false;
            }
        });
        String localConfigStr = System.getenv("localConfig");
        boolean localConfig = !Strings.isNullOrEmpty(localConfigStr) && Boolean.parseBoolean(localConfigStr);
        roseyConfigSourceProvider = new RoseyConfigSourceProvider("edge", System.getenv("APP_NAME"));
        if(localConfig) {
            bootstrap.setConfigurationSourceProvider(
                    new SubstitutingSourceProvider(bootstrap.getConfigurationSourceProvider(), new EnvironmentVariableSubstitutor()));
        } else {
            bootstrap.setConfigurationSourceProvider(roseyConfigSourceProvider);
        }

        ServiceDiscoveryBundle<AppConfig> serviceDiscoveryBundle;
        //TODO Revert later
        serviceDiscoveryBundle = new ServiceDiscoveryBundle<AppConfig>() {
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
                        e.printStackTrace();

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

        bootstrap.addBundle(new CheckmateBundle<AppConfig>(serviceDiscoveryBundle.getCurator()) {
            @Override
            public CheckmateBundleConfiguration getCheckmateConfiguration(AppConfig apiConfiguration) {
                return apiConfiguration.getCheckmate();
            }
        });
    }

    @Override
    public void run(AppConfig configuration, Environment environment) throws Exception {
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


        List<MessageHandlingActor> messageHandlingActorList = Lists.newArrayList();

        Map<String, ActorConfig> actors = getActors(configuration);
        AtomicInteger rmqConcurrency = new AtomicInteger();
        actors.forEach((a, actorConfig) -> {
            rmqConcurrency.addAndGet(actorConfig.getConcurrency());
        });

        RMQConnection rmqConnection = new RMQConnection(configuration.getRmqConfig(), metrics,
                                                        Executors.newFixedThreadPool(rmqConcurrency.get())
        );
        environment.lifecycle()
                .manage(rmqConnection);

        actors.forEach((a, actorConfig) -> messageHandlingActorList.add(
                new CallbackMessageHandlingActor(a, actorConfig, rmqConnection, objectMapper, callbackHandler, persistenceProvider)));


        ActionMessagePublisher.initialize(messageHandlingActorList);

        CallbackRequestResource callbackRequestResource = CallbackRequestResource.builder()
                .callbackHandler(callbackHandler)
                .jsonObjectMapper(objectMapper)
                .persistenceProvider(persistenceProvider)
                .msgPackObjectMapper(objectMapper)
                .build();

        environment.lifecycle()
                .manage(new RMQWrapper(rmqConnection));
        environment.jersey()
                .register(callbackRequestResource);

    }

    private Map<String, ActorConfig> getActors(AppConfig configuration) {
        Map<String, ActorConfig> actors = Maps.newHashMap();
        for(RevolverServiceConfig revolverServiceConfig : configuration.getRevolver()
                .getServices()) {
            actors.putAll(CollectionUtils.nullSafeMap(revolverServiceConfig.getActors()));
        }
        return actors;
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
        }
        throw new IllegalArgumentException("Invalid mailbox configuration");
    }

}
