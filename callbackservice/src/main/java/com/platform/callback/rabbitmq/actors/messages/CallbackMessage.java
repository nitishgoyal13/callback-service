package com.platform.callback.rabbitmq.actors.messages;
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

import com.platform.callback.rabbitmq.ActionMessage;
import com.platform.callback.rabbitmq.ActionMessageVisitor;
import com.platform.callback.rabbitmq.actors.ActionType;
import lombok.Builder;
import lombok.Data;

/***
 Created by nitish.goyal on 05/02/19
 ***/
@Data
@Builder
public class CallbackMessage extends ActionMessage {

    private String requestId;

    protected CallbackMessage() {
        super(ActionType.CALLBACK);
    }

    @Builder
    public CallbackMessage(String requestId) {
        this();
        this.requestId = requestId;
    }

    @Override
    public <T> T accept(ActionMessageVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
