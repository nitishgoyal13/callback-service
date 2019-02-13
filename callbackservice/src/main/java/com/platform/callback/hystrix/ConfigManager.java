package com.platform.callback.hystrix;


import lombok.Getter;
import lombok.Setter;

/***
 Created by nitish.goyal on 02/02/19
 ***/
public class ConfigManager {

    @Setter
    @Getter
    private static boolean hystrixEnabled = true;

}
