/*
 * Copyright 2016 Phaneesh Nagaraja <phaneesh.n@gmail.com>.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.platform.callback.resources;

import com.codahale.metrics.annotation.Metered;
import com.google.common.io.ByteStreams;
import com.platform.callback.services.DownstreamResponseHandler;
import io.dropwizard.msgpack.MsgPackMediaType;
import io.dropwizard.revolver.RevolverBundle;
import io.dropwizard.revolver.base.core.RevolverCallbackResponse;
import io.dropwizard.revolver.callback.InlineCallbackHandler;
import io.dropwizard.revolver.persistence.PersistenceProvider;
import io.dropwizard.revolver.util.HeaderUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * @author phaneesh
 */
@Path("/revolver")
@Slf4j
@Singleton
@Builder
@Api(value = "RequestCallback", description = "Revolver gateway api for callbacks on mailbox requests")
public class CallbackResource {

    private static final String RESPONSE_CODE_HEADER = "X-RESPONSE-CODE";

    private final PersistenceProvider persistenceProvider;

    private final InlineCallbackHandler callbackHandler;

    private final DownstreamResponseHandler downstreamResponseHandler;

    @Path("/v1/callback/{requestId}")
    @POST
    @Metered
    @ApiOperation(value = "Callback for updating responses for a given mailbox request")
    @Produces({MediaType.APPLICATION_JSON, MsgPackMediaType.APPLICATION_MSGPACK, MediaType.APPLICATION_XML})
    @Consumes({MediaType.APPLICATION_JSON, MsgPackMediaType.APPLICATION_MSGPACK, MediaType.APPLICATION_XML})
    public Response handleCallback(@PathParam("requestId") final String requestId,
                                   @HeaderParam(RESPONSE_CODE_HEADER) final String responseCode, @Context final HttpHeaders headers,
                                   @Context final HttpServletRequest request) {
        try {
            final val callbackRequest = persistenceProvider.request(requestId);
            if(callbackRequest == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .build();
            }
            byte[] responseBody = ByteStreams.toByteArray(request.getInputStream());
            val response = RevolverCallbackResponse.builder()
                    .body(responseBody)
                    .headers(headers.getRequestHeaders())
                    .statusCode(responseCode != null ? Integer.parseInt(responseCode) : Response.Status.OK.getStatusCode())
                    .build();
            val mailboxTtl = HeaderUtil.getTTL(callbackRequest);

            val apiMap = RevolverBundle.matchPath(callbackRequest.getService(), callbackRequest.getApi());
            log.info("apiMap : " + apiMap);
            log.info("Callbackservice : " + callbackRequest.getService() + ", api : " + callbackRequest.getApi() + ", api : " +
                     apiMap.getApi() + ", mode : " + callbackRequest.getMode());
            downstreamResponseHandler.saveResponse(requestId, response, callbackRequest.getMode(), mailboxTtl, apiMap.getApi());
            log.info("Callback added in the queue for processing");
            return Response.accepted()
                    .build();
        } catch (Exception e) {
            log.error("Callback error", e);
            return Response.serverError()
                    .build();
        }
    }
}
