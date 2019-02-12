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

import com.platform.callback.rabbitmq.ActionMessagePublisher;
import com.platform.callback.rabbitmq.actors.messages.CallbackMessage;
import lombok.AllArgsConstructor;

import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;

/***
 Created by nitish.goyal on 02/02/19
 ***/
@Path("/test")
@AllArgsConstructor
@Singleton
public class TestLocalSetupResource {


    @GET
    @Path("/publish")
    public void publish() {
        ActionMessagePublisher.publish(CallbackMessage.builder()
                                               .requestId("123")
                                               .build());
    }

    @GET
    @Path("/read")
    public void read() {
        ActionMessagePublisher.publish(CallbackMessage.builder()
                                               .requestId("123")
                                               .build());
    }
}
