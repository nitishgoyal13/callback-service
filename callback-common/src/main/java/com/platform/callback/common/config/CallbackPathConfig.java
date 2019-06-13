package com.platform.callback.common.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/***
 Created by nitish.goyal on 23/05/19
 ***/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CallbackPathConfig {

    private List<String> pathIds;

    private String queueId;

}
