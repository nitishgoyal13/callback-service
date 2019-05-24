package com.platform.callback.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hystrix.configurator.config.HystrixConfig;
import io.dropwizard.Configuration;
import io.dropwizard.actors.config.RMQConfig;
import io.dropwizard.discovery.bundle.ServiceDiscoveryConfiguration;
import io.dropwizard.revolver.core.config.RevolverConfig;
import io.dropwizard.riemann.RiemannConfig;
import lombok.*;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

/**
 * App configuration class
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class AppConfig extends Configuration {


    @JsonProperty("revolver")
    private RevolverConfig revolver = new RevolverConfig();

    @Valid
    private RMQConfig rmqConfig;

    @JsonProperty("appName")
    private String appName = "apicallback";

    @NotNull
    @Valid
    @JsonProperty
    private HystrixConfig hystrixConfig = new HystrixConfig();

    @JsonProperty("discovery")
    private ServiceDiscoveryConfiguration discovery;

    @Valid
    private RiemannConfig riemann;

    @NonNull
    @JsonProperty("callbackConfig")
    private CallbackConfig callbackConfig;


}