package com.platform.callback.common.exception;

import javax.ws.rs.core.Response;

/***
 Created by nitish.goyal on 24/05/19
 ***/
public class RMQException extends CallbackException {

    public RMQException(Response.Status status, String message) {
        super(status, message);
    }
}
