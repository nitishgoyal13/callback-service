package com.platform.callback.exception;

import lombok.Builder;

import javax.ws.rs.core.Response;

/***
 Created by nitish.goyal on 24/05/19
 ***/
@Builder
public class RMQException extends CallbackException {

    public RMQException(Response.Status status, String message) {
        super(status, message);
    }
}
