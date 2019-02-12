package com.platform.callback.rabbitmq.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * Created by santanu on 22/3/16.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RMQConfig {
    @NotNull
    @NotEmpty
    private List<Broker> brokers;

    @NotEmpty
    @NotNull
    private String exchange;

    @NotEmpty
    @NotNull
    private String userName;


    @NotNull
    private int threadPoolSize;

    @NotEmpty
    @NotNull
    private String password;

    private boolean sslEnabled = false;


}
