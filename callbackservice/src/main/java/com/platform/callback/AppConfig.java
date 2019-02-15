package com.platform.callback;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hystrix.configurator.config.HystrixConfig;
import com.platform.callback.rabbitmq.config.CallbackQueueConfig;
import io.dropwizard.Configuration;
import io.dropwizard.actors.actor.ActorConfig;
import io.dropwizard.actors.config.RMQConfig;
import io.dropwizard.discovery.bundle.ServiceDiscoveryConfiguration;
import io.dropwizard.primer.model.PrimerBundleConfiguration;
import io.dropwizard.revolver.core.config.RevolverConfig;
import io.dropwizard.riemann.RiemannConfig;
import lombok.*;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.Map;

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
    private String appName = "callback";


    @NotNull
    @Valid
    @JsonProperty
    private HystrixConfig hystrixConfig = new HystrixConfig();

    @NotNull
    @Valid
    private ServiceDiscoveryConfiguration serviceDiscovery;

    @NotNull
    @Valid
    private RiemannConfig riemann;

    @NotNull
    @NotEmpty
    @Valid
    private Map<String, ActorConfig> actors;

    @NotNull
    private CallbackQueueConfig callbackQueueConfig;


}
