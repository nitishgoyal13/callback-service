package com.platform.callback.domain;

import io.dropwizard.revolver.base.core.RevolverCallbackRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/***
 Created by nitish.goyal on 02/02/19
 ***/
@AllArgsConstructor
@Data
public class CallbackRequest extends RevolverCallbackRequest {

    private String queue;

}
