package com.platform.callback.core;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.hystrix.configurator.core.HystrixConfigurationFactory;
import com.phonepe.rosey.dwconfig.RoseyConfigSourceProvider;
import com.platform.callback.common.config.AppConfig;
import com.platform.callback.common.config.CallbackConfig;
import com.platform.callback.common.executor.CallbackExecutor;
import com.platform.callback.common.executor.CallbackExecutorFactory;
import com.platform.callback.common.guice.ExecutorInjectorModule;
import com.platform.callback.common.handler.InlineCallbackHandler;
import com.platform.callback.core.resources.CallbackRequestResource;
import com.platform.callback.core.resources.CallbackResource;
import com.platform.callback.core.services.DownstreamResponseHandler;
import io.dropwizard.Application;
import io.dropwizard.actors.RabbitmqActorBundle;
import io.dropwizard.actors.config.RMQConfig;
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
import java.util.Set;
import java.util.stream.Collectors;

import static com.platform.callback.common.utils.ConstantUtils.APIS;

/**
 * Created by nitishgoyal13 on 31/1/19.
 */
@Slf4j
public class App extends Application<AppConfig> {

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
        /*
        boolean localConfig = !Strings.isNullOrEmpty(localConfigStr) && Boolean.parseBoolean(localConfigStr);
        if(localConfig) {
            bootstrap.setConfigurationSourceProvider(
                    new SubstitutingSourceProvider(bootstrap.getConfigurationSourceProvider(), new EnvironmentVariableSubstitutor()));
        } else {
            bootstrap.setConfigurationSourceProvider(roseyConfigSourceProvider);
        }*/

        //TODO Delete later
        bootstrap.setConfigurationSourceProvider(
                new SubstitutingSourceProvider(bootstrap.getConfigurationSourceProvider(), new EnvironmentVariableSubstitutor()));


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


        initializeRevolverBundle(bootstrap, roseyConfigSourceProvider, serviceDiscoveryBundle);
        initializePrimerBundle(bootstrap, serviceDiscoveryBundle);

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

    }

    private void initializeRevolverBundle(Bootstrap<AppConfig> bootstrap, RoseyConfigSourceProvider roseyConfigSourceProvider,
                                          ServiceDiscoveryBundle<AppConfig> serviceDiscoveryBundle) {
        bootstrap.addBundle(new RevolverBundle<AppConfig>() {

            @Override
            public CuratorFramework getCurator() {
                return serviceDiscoveryBundle.getCurator();
            }

            @Override
            public RevolverConfig getRevolverConfig(AppConfig appConfig) {
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
    }

    @Override
    public void run(AppConfig configuration, Environment environment) {
        val objectMapper = environment.getObjectMapper();

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

        Injector injector = Guice.createInjector(new ExecutorInjectorModule(callbackHandler, callbackConfig, persistenceProvider));
        CallbackExecutorFactory callbackExecutorFactory = new CallbackExecutorFactory(injector);
        CallbackExecutor callbackExecutor = callbackExecutorFactory.getExecutor(callbackConfig.getCallbackType());

        callbackExecutor.initialize(configuration, environment);

        DownstreamResponseHandler downstreamResponseHandler = DownstreamResponseHandler.builder()
                .persistenceProvider(persistenceProvider)
                .callbackHandler(callbackHandler)
                .callbackExecutor(callbackExecutor)
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

    public void initializePrimerBundle(Bootstrap<AppConfig> bootstrap, ServiceDiscoveryBundle<AppConfig> serviceDiscoveryBundle) {
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
                                .map(a -> APIS + service.getService() + "/" + a.getPath())
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
                        .url(APIS + serviceConfig.getService() + "/" + apiConfig.getPath())
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
                        .url(APIS + serviceConfig.getService() + "/" + apiConfig.getPath())
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
                        .url(APIS + serviceConfig.getService() + "/" + apiConfig.getPath())
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
