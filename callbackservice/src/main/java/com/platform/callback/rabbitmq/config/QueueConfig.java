package com.platform.callback.rabbitmq.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;

/***
 Created by nitish.goyal on 02/02/19
 ***/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class QueueConfig {

    @NotNull
    private String queue;

    private int size = 100;

}
