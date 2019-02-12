package com.platform.callback.services;
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

import com.platform.callback.domain.CallbackRequest;
import com.platform.callback.exception.CallbackException;
import com.platform.callback.exception.ResponseCode;
import io.dropwizard.revolver.base.core.RevolverCallbackRequest;
import io.dropwizard.revolver.persistence.AeroSpikePersistenceProvider;
import lombok.AllArgsConstructor;
import lombok.Builder;

/***
 Created by nitish.goyal on 02/02/19
 ***/
@AllArgsConstructor
@Builder
public class ForwardCallResponseHandler implements IForwardCallResponseHandler {

    private final AeroSpikePersistenceProvider persistenceProvider;


    @Override
    public void handle(String requestId) {
        RevolverCallbackRequest revolverCallbackRequest = persistenceProvider.request(requestId);

        CallbackRequest callbackRequest;
        if(revolverCallbackRequest instanceof CallbackRequest) {
            callbackRequest = (CallbackRequest)revolverCallbackRequest;
        } else {
            throw new CallbackException(ResponseCode.INVALID_REQUEST, "Not a callback request");
        }



    }
}
