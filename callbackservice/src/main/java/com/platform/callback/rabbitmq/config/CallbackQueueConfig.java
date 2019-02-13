package com.platform.callback.rabbitmq.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/***
 Created by nitish.goyal on 02/02/19
 ***/
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CallbackQueueConfig {

    private List<QueueConfig> configList;


}
