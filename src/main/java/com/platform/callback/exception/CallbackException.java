package com.platform.callback.exception;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.ws.rs.core.Response;

/***
 Created by nitish.goyal on 02/02/19
 ***/
@AllArgsConstructor
@Builder
@Data
@EqualsAndHashCode(callSuper = true)
public class CallbackException extends RuntimeException {

    private Response.Status status;

    private String message;
}
