package com.platform.callback;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.hystrix.configurator.core.HystrixConfigurationFactory;
import com.phonepe.rosey.dwconfig.RoseyConfigSourceProvider;
import com.platform.callback.rabbitmq.ActionMessagePublisher;
import com.platform.callback.rabbitmq.RMQWrapper;
import com.platform.callback.rabbitmq.actors.impl.CallbackMessageHandlingActor;
import com.platform.callback.rabbitmq.actors.impl.MessageHandlingActor;
import com.platform.callback.resources.CallbackRequestResource;
import com.platform.callback.resources.TestLocalSetupResource;
import io.dropwizard.Application;
import io.dropwizard.actors.RabbitmqActorBundle;
import io.dropwizard.actors.actor.ActorConfig;
import io.dropwizard.actors.config.RMQConfig;
import io.dropwizard.actors.connectivity.RMQConnection;
import io.dropwizard.actors.retry.RetryStrategyFactory;
import io.dropwizard.configuration.ConfigurationSourceProvider;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.discovery.bundle.ServiceDiscoveryBundle;
import io.dropwizard.oor.OorBundle;
import io.dropwizard.revolver.RevolverBundle;
import io.dropwizard.revolver.aeroapike.AerospikeConnectionManager;
import io.dropwizard.revolver.callback.InlineCallbackHandler;
import io.dropwizard.revolver.core.config.AerospikeMailBoxConfig;
import io.dropwizard.revolver.core.config.RevolverConfig;
import io.dropwizard.revolver.filters.RevolverRequestFilter;
import io.dropwizard.revolver.handler.ConfigSource;
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

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Created by nitishgoyal13 on 31/1/19.
 */
@Slf4j
public class App extends Application<AppConfig> {
    private static final String SERVICE_NAME = "callback";
    private static ConfigurationSourceProvider configurationSourceProvider;
    private ServiceDiscoveryBundle<AppConfig> serviceDiscoveryBundle;
    private RabbitmqActorBundle<AppConfig> rabbitmqActorBundle;

    @Override
    public void initialize(Bootstrap<AppConfig> bootstrap) {
        bootstrap.addBundle(new OorBundle<AppConfig>() {
            @Override
            public boolean withOor() {
                return false;
            }
        });
        configurationSourceProvider = new SubstitutingSourceProvider(bootstrap.getConfigurationSourceProvider(),
                                                                     new EnvironmentVariableSubstitutor()
        );
        String localConfigStr = System.getenv("localConfig");
        boolean localConfig = !Strings.isNullOrEmpty(localConfigStr) && Boolean.parseBoolean(localConfigStr);
        if(localConfig) {
            bootstrap.setConfigurationSourceProvider(
                    new SubstitutingSourceProvider(bootstrap.getConfigurationSourceProvider(), new EnvironmentVariableSubstitutor()));
        } else {
            bootstrap.setConfigurationSourceProvider(new SubstitutingSourceProvider(new RoseyConfigSourceProvider("callback", "callback"),
                                                                                    new EnvironmentVariableSubstitutor()
            ));
        }

        /*serviceDiscoveryBundle = new ServiceDiscoveryBundle<AppConfig>() {
            @Override
            protected ServiceDiscoveryConfiguration getRangerConfiguration(AppConfig configuration) {
                return configuration.getServiceDiscovery();
            }

            @Override
            protected String getServiceName(AppConfig configuration) {
                return configuration.getAppName();
            }

            @Override
            protected int getPort(AppConfig configuration) {
                return configuration.getServiceDiscovery()
                        .getPublishedPort();
            }

        };
        bootstrap.addBundle(serviceDiscoveryBundle);*/

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
                        return configurationSourceProvider.open("config/local.yml");
                    } catch (IOException e) {
                        e.printStackTrace();

                    }
                    return null;
                };

            }
        });

        /*bootstrap.addBundle(new PrimerBundle<AppConfig>() {

            @Override
            public CuratorFramework getCurator(AppConfig appConfig) {
                return serviceDiscoveryBundle.getCurator();
            }

            @Override
            public PrimerBundleConfiguration getPrimerConfiguration(AppConfig appConfig) {
                return appConfig.getPrimer();
            }

            @Override
            public Set<String> withWhiteList(AppConfig appConfig) {
                return appConfig.getRevolver()
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
            public PrimerAuthorizationMatrix withAuthorization(AppConfig appConfig) {
                val staticAuth = appConfig.getRevolver()
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
                val dynamicAuth = appConfig.getRevolver()
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
                val autoAuth = appConfig.getRevolver()
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
                if(appConfig.getPrimer() != null && appConfig.getPrimer()
                                                            .getAuthorizations() != null) {
                    if(appConfig.getPrimer()
                               .getAuthorizations()
                               .getAutoAuthorizations() != null) {
                        autoAuth.addAll(appConfig.getPrimer()
                                                .getAuthorizations()
                                                .getAutoAuthorizations());
                    }
                    if(appConfig.getPrimer()
                               .getAuthorizations()
                               .getStaticAuthorizations() != null) {
                        autoAuth.addAll(appConfig.getPrimer()
                                                .getAuthorizations()
                                                .getStaticAuthorizations());
                    }
                    if(appConfig.getPrimer()
                               .getAuthorizations()
                               .getAuthorizations() != null) {
                        dynamicAuth.addAll(appConfig.getPrimer()
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

        });*/

        rabbitmqActorBundle = new RabbitmqActorBundle<AppConfig>() {
            @Override
            protected RMQConfig getConfig(AppConfig appConfig) {
                return appConfig.getRmqConfig();
            }
        };
        bootstrap.addBundle(this.rabbitmqActorBundle);

        bootstrap.addBundle(new RiemannBundle<AppConfig>() {

            @Override
            public RiemannConfig getRiemannConfiguration(AppConfig configuration) {
                return configuration.getRiemann();
            }
        });
    }

    @Override
    public void run(AppConfig configuration, Environment environment) throws Exception {
        val executionEnv = System.getenv("CONFIG_ENV");
        val objectMapper = environment.getObjectMapper();
        val metrics = environment.metrics();

        RetryStrategyFactory retryStrategyFactory = new RetryStrategyFactory();

        RMQConnection rmqConnection = new RMQConnection(configuration.getRmqConfig(), metrics, Executors.newFixedThreadPool(
                configuration.getRmqConfig()
                        .getThreadPoolSize()));
        environment.lifecycle()
                .manage(rmqConnection);
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
        for(Map.Entry<String, ActorConfig> actor : configuration.getActors()
                .entrySet()) {
            messageHandlingActorList.add(
                    new CallbackMessageHandlingActor(actor.getKey(), actor.getValue(), rmqConnection, objectMapper, callbackHandler,
                                                     persistenceProvider
                    ));
        }

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
        environment.jersey()
                .register(new TestLocalSetupResource());


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
