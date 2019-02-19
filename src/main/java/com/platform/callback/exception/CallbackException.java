package com.platform.callback.exception;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

/***
 Created by nitish.goyal on 02/02/19
 ***/
@AllArgsConstructor
@Builder
@Data
@EqualsAndHashCode(callSuper = true)
public class CallbackException extends RuntimeException {

    private ResponseCode responseCode;

    private String message;
}
