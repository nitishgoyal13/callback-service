package com.platform.callback.resources;

import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Singleton;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

/***
 Created by nitish.goyal on 02/02/19
 ***/
@Path("/execute")
@Singleton
@Slf4j
@Api(value = "Callback Execution Gateway", description = "Callback execution APIs")
public class CallbackExecuteResource {

    @Path("/submit")
    public Response submit(String requestId) {
        return null;
    }
}
