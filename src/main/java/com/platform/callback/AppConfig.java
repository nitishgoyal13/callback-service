package com.platform.callback;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hystrix.configurator.config.HystrixConfig;
import io.dropwizard.Configuration;
import io.dropwizard.actors.config.RMQConfig;
import io.dropwizard.checkmate.model.CheckmateBundleConfiguration;
import io.dropwizard.discovery.bundle.ServiceDiscoveryConfiguration;
import io.dropwizard.primer.model.PrimerBundleConfiguration;
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


    private RevolverConfig revolver = new RevolverConfig();

    private PrimerBundleConfiguration primer = new PrimerBundleConfiguration();

    @NotNull
    @Valid
    private RMQConfig rmqConfig;

    @JsonProperty("appName")
    @Getter
    @Setter
    private String appName = "apicallback";


    @NotNull
    @Valid
    @JsonProperty
    private HystrixConfig hystrixConfig = new HystrixConfig();

    @JsonProperty("discovery")
    @Getter
    @Setter
    private ServiceDiscoveryConfiguration discovery;

    @NotNull
    @Valid
    private RiemannConfig riemann;

    @JsonProperty("checkmate")
    @Getter
    @Setter
    private CheckmateBundleConfiguration checkmate;


}
