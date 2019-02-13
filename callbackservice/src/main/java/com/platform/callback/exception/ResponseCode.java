package com.platform.callback.exception;

import lombok.AllArgsConstructor;

/***
 Created by nitish.goyal on 02/02/19
 ***/
@AllArgsConstructor
public enum  ResponseCode {

    INVALID_REQUEST(401, "Invalid Request"),
    QUEUE_EXCEPTION(402, "Error while publishing to Queue");

    private int code;
    private String error;



}
