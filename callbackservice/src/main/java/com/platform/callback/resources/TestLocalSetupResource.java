package com.platform.callback.resources;

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
