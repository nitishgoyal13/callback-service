package com.platform.callback.common.exception;

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

    private final Response.Status status;

    private final String message;
}
