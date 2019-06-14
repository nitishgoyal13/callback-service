package com.platform.callback.core.resources;

import com.codahale.metrics.annotation.Metered;
import com.google.common.io.ByteStreams;
import com.platform.callback.common.handler.InlineCallbackHandler;
import com.platform.callback.core.services.DownstreamResponseHandler;
import io.dropwizard.msgpack.MsgPackMediaType;
import io.dropwizard.revolver.base.core.RevolverCallbackResponse;
import io.dropwizard.revolver.http.RevolversHttpHeaders;
import io.dropwizard.revolver.persistence.PersistenceProvider;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.AllArgsConstructor;
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
 * @author nitishgoyal13
 */

@Path("/revolver")
@Slf4j
@Singleton
@Builder
@Api(value = "RequestCallback")
@AllArgsConstructor
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

            byte[] responseBody = ByteStreams.toByteArray(request.getInputStream());
            val response = RevolverCallbackResponse.builder()
                    .body(responseBody)
                    .headers(headers.getRequestHeaders())
                    .statusCode(responseCode != null ? Integer.parseInt(responseCode) : Response.Status.OK.getStatusCode())
                    .build();

            final val callbackRequest = persistenceProvider.request(requestId);
            if(callbackRequest == null) {
                Response.status(Response.Status.BAD_REQUEST)
                        .build();
            }

            downstreamResponseHandler.saveResponse(requestId, response, headers.getRequestHeaders()
                    .getFirst(RevolversHttpHeaders.CALLBACK_URI_HEADER));
            log.info("Callback added for processing");
            return Response.accepted()
                    .build();
        } catch (Exception e) {
            log.error("Callback error", e);
            return Response.serverError()
                    .build();
        }
    }
}
