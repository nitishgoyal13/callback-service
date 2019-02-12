package com.platform.callback.resources;
/*
 * Copyright 2014 Flipkart Internet Pvt. Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
