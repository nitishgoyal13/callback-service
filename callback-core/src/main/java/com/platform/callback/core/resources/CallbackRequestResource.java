package com.platform.callback.core.resources;

import com.codahale.metrics.annotation.Metered;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.platform.callback.common.handler.InlineCallbackHandler;
import com.platform.callback.core.services.DownstreamResponseHandler;
import com.utils.StringUtils;
import io.dropwizard.jersey.PATCH;
import io.dropwizard.msgpack.MsgPackMediaType;
import io.dropwizard.revolver.RevolverBundle;
import io.dropwizard.revolver.base.core.RevolverAckMessage;
import io.dropwizard.revolver.base.core.RevolverCallbackRequest;
import io.dropwizard.revolver.base.core.RevolverCallbackResponse;
import io.dropwizard.revolver.base.core.RevolverRequestState;
import io.dropwizard.revolver.core.tracing.TraceInfo;
import io.dropwizard.revolver.http.RevolverHttpCommand;
import io.dropwizard.revolver.http.RevolversHttpHeaders;
import io.dropwizard.revolver.http.config.RevolverHttpApiConfig;
import io.dropwizard.revolver.http.model.RevolverHttpRequest;
import io.dropwizard.revolver.http.model.RevolverHttpResponse;
import io.dropwizard.revolver.persistence.PersistenceProvider;
import io.dropwizard.revolver.util.ResponseTransformationUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Singleton;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import static com.platform.callback.common.utils.ConstantUtils.MESSAGE;

@Path("/apis")
@Slf4j
@Singleton
@Api(value = "Revolver Gateway")
@Builder
@AllArgsConstructor
public class CallbackRequestResource {

    private static final Map<String, String> BAD_REQUEST_RESPONSE = Collections.singletonMap(MESSAGE, "Bad Request");
    private static final Map<String, String> DUPLICATE_REQUEST_RESPONSE = Collections.singletonMap(MESSAGE, "Duplicate");
    private static final Map<String, String> SERVICE_UNAVAILABLE_RESPONSE = Collections.singletonMap(MESSAGE, "Service Unavailable");
    private final ObjectMapper jsonObjectMapper;
    private final ObjectMapper msgPackObjectMapper;
    private final PersistenceProvider persistenceProvider;
    private final InlineCallbackHandler callbackHandler;
    private final DownstreamResponseHandler downstreamResponseHandler;


    @GET
    @Path(value = "/{service}/{path: .*}")
    @Metered
    @ApiOperation(value = "Revolver GET api endpoint")
    public Response get(@PathParam("service") final String service, @PathParam("path") final String path,
                        @Context final HttpHeaders headers, @Context final UriInfo uriInfo) throws Exception {
        return processRequest(service, RevolverHttpApiConfig.RequestMethod.GET, path, headers, uriInfo, null);
    }

    @HEAD
    @Path(value = "/{service}/{path: .*}")
    @Metered
    @ApiOperation(value = "Revolver HEAD api endpoint")
    public Response head(@PathParam("service") final String service, @PathParam("path") final String path,
                         @Context final HttpHeaders headers, @Context final UriInfo uriInfo) throws Exception {
        return processRequest(service, RevolverHttpApiConfig.RequestMethod.HEAD, path, headers, uriInfo, null);
    }

    @POST
    @Path(value = "/{service}/{path: .*}")
    @Metered
    @ApiOperation(value = "Revolver POST api endpoint")
    public Response post(@PathParam("service") final String service, @PathParam("path") final String path,
                         @Context final HttpHeaders headers, @Context final UriInfo uriInfo, final byte[] body) throws Exception {
        return processRequest(service, RevolverHttpApiConfig.RequestMethod.POST, path, headers, uriInfo, body);
    }

    @PUT
    @Path(value = "/{service}/{path: .*}")
    @Metered
    @ApiOperation(value = "Revolver PUT api endpoint")
    public Response put(@PathParam("service") final String service, @PathParam("path") final String path,
                        @Context final HttpHeaders headers, @Context final UriInfo uriInfo, final byte[] body) throws Exception {
        return processRequest(service, RevolverHttpApiConfig.RequestMethod.PUT, path, headers, uriInfo, body);
    }

    @DELETE
    @Path(value = "/{service}/{path: .*}")
    @Metered
    @ApiOperation(value = "Revolver DELETE api endpoint")
    public Response delete(@PathParam("service") final String service, @PathParam("path") final String path,
                           @Context final HttpHeaders headers, @Context final UriInfo uriInfo) throws Exception {
        return processRequest(service, RevolverHttpApiConfig.RequestMethod.DELETE, path, headers, uriInfo, null);
    }

    @PATCH
    @Path(value = "/{service}/{path: .*}")
    @Metered
    @ApiOperation(value = "Revolver PATCH api endpoint")
    public Response patch(@PathParam("service") final String service, @PathParam("path") final String path,
                          @Context final HttpHeaders headers, @Context final UriInfo uriInfo, final byte[] body) throws Exception {
        return processRequest(service, RevolverHttpApiConfig.RequestMethod.PATCH, path, headers, uriInfo, body);
    }

    @OPTIONS
    @Path(value = "/{service}/{path: .*}")
    @Metered
    @ApiOperation(value = "Revolver OPTIONS api endpoint")
    public Response options(@PathParam("service") final String service, @PathParam("path") final String path,
                            @Context final HttpHeaders headers, @Context final UriInfo uriInfo, final byte[] body) throws Exception {
        return processRequest(service, RevolverHttpApiConfig.RequestMethod.OPTIONS, path, headers, uriInfo, body);
    }


    private Response processRequest(final String service, final RevolverHttpApiConfig.RequestMethod method, final String path,
                                    final HttpHeaders headers, final UriInfo uriInfo, final byte[] body) throws Exception {
        val apiMap = RevolverBundle.matchPath(service, path);
        if(apiMap == null) {
            log.error("BadRequest for service : " + service + " and path: " + path);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ResponseTransformationUtil.transform(BAD_REQUEST_RESPONSE, getMediaType(headers), jsonObjectMapper,
                                                                 msgPackObjectMapper
                                                                ))
                    .build();
        }
        String serviceKey = service + "." + apiMap.getApi()
                .getApi();
        log.error("ServiceKey : " + serviceKey);
        if(RevolverBundle.apiStatus.containsKey(serviceKey) && !RevolverBundle.apiStatus.get(serviceKey)) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(ResponseTransformationUtil.transform(SERVICE_UNAVAILABLE_RESPONSE, getMediaType(headers), jsonObjectMapper,
                                                                 msgPackObjectMapper
                                                                ))
                    .build();
        }
        val callMode = headers.getRequestHeaders()
                .getFirst(RevolversHttpHeaders.CALL_MODE_HEADER);
        if(Strings.isNullOrEmpty(callMode)) {
            return executeInline(service, apiMap.getApi(), method, path, headers, uriInfo, body);
        }


        switch (callMode.toUpperCase()) {
            case RevolverHttpCommand.CALL_MODE_CALLBACK:
                if(Strings.isNullOrEmpty(headers.getHeaderString(RevolversHttpHeaders.CALLBACK_URI_HEADER))) {
                    log.error("Empty CALLBACK_URI_HEADER CALL_MODE_CALLBACK : " + service + ":" + path);
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity(ResponseTransformationUtil.transform(BAD_REQUEST_RESPONSE, getMediaType(headers), jsonObjectMapper,
                                                                         msgPackObjectMapper
                                                                        ))
                            .build();
                }
                return executeCommandAsync(service, apiMap.getApi(), method, path, headers, uriInfo, body);
            case RevolverHttpCommand.CALL_MODE_CALLBACK_SYNC:
                if(Strings.isNullOrEmpty(headers.getHeaderString(RevolversHttpHeaders.CALLBACK_URI_HEADER))) {
                    log.error("Empty CALLBACK_URI_HEADER CALL_MODE_CALLBACK_SYNC : " + service + ":" + path);
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity(ResponseTransformationUtil.transform(BAD_REQUEST_RESPONSE, getMediaType(headers), jsonObjectMapper,
                                                                         msgPackObjectMapper
                                                                        ))
                            .build();
                }
                return executeCallbackSync(service, apiMap.getApi(), method, path, headers, uriInfo, body);
            default:
                break;
        }
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(ResponseTransformationUtil.transform(BAD_REQUEST_RESPONSE, getMediaType(headers), jsonObjectMapper,
                                                             msgPackObjectMapper
                                                            ))
                .build();
    }

    private String getMediaType(HttpHeaders headers) {
        return headers.getMediaType() != null ? headers.getMediaType()
                .toString() : MediaType.APPLICATION_JSON;
    }

    private Response executeInline(final String service, final RevolverHttpApiConfig api, final RevolverHttpApiConfig.RequestMethod method,
                                   final String path, final HttpHeaders headers, final UriInfo uriInfo, final byte[] body)
            throws IOException, TimeoutException {
        val sanatizedHeaders = new MultivaluedHashMap<String, String>();
        log.info("ExecutingInline : " + service + ":" + path);
        headers.getRequestHeaders()
                .forEach(sanatizedHeaders::put);
        cleanHeaders(sanatizedHeaders, api);
        val httpCommand = RevolverBundle.getHttpCommand(service, api.getApi());
        val response = httpCommand.execute(RevolverHttpRequest.builder()
                                                   .traceInfo(TraceInfo.builder()
                                                                      .requestId(getRequestId(headers))
                                                                      .transactionId(
                                                                              headers.getHeaderString(RevolversHttpHeaders.TXN_ID_HEADER))
                                                                      .timestamp(System.currentTimeMillis())
                                                                      .build())
                                                   .api(api.getApi())
                                                   .service(service)
                                                   .path(path)
                                                   .method(method)
                                                   .headers(sanatizedHeaders)
                                                   .queryParams(uriInfo.getQueryParameters())
                                                   .body(body)
                                                   .build());
        return transform(headers, response, api.getApi(), path, method);
    }


    private Response transform(HttpHeaders headers, RevolverHttpResponse response, String api, String path,
                               RevolverHttpApiConfig.RequestMethod method) throws IOException {
        val httpResponse = Response.status(response.getStatusCode());
        //Add all the headers except content type header
        if(response.getHeaders() != null) {
            response.getHeaders()
                    .keySet()
                    .stream()
                    .filter(h -> !h.equalsIgnoreCase(HttpHeaders.CONTENT_TYPE))
                    .filter(h -> !h.equalsIgnoreCase(HttpHeaders.CONTENT_LENGTH))
                    .forEach(h -> httpResponse.header(h, response.getHeaders()
                            .getFirst(h)));
        }
        httpResponse.header("X-REQUESTED-PATH", path);
        httpResponse.header("X-REQUESTED-METHOD", method);
        httpResponse.header("X-REQUESTED-API", api);
        final String responseMediaType = response.getHeaders() != null && Strings.isNullOrEmpty(response.getHeaders()
                                                                                                        .getFirst(
                                                                                                                HttpHeaders.CONTENT_TYPE)
                                                                                               ) ? MediaType.TEXT_HTML : response
                                                 .getHeaders()
                                                 .getFirst(HttpHeaders.CONTENT_TYPE);
        final String requestMediaType = getRequestMediaType(headers);
        /*If no no accept was specified in request; just send it as the same content type as response
        Also send it as the content type as response content type if there requested content type is the same*/
        if(Strings.isNullOrEmpty(requestMediaType) || requestMediaType.equals(responseMediaType)) {
            httpResponse.header(HttpHeaders.CONTENT_TYPE, responseMediaType);
            httpResponse.entity(response.getBody());
            return httpResponse.build();
        }
        Object responseData = getResponseData(response, responseMediaType);
        handleResponse(response, httpResponse, requestMediaType, responseData);
        return httpResponse.build();
    }

    private String getRequestMediaType(HttpHeaders headers) {
        if(headers == null || StringUtils.isEmpty(headers.getHeaderString(HttpHeaders.ACCEPT))) {
            return null;
        }
        return headers.getHeaderString(HttpHeaders.ACCEPT);
    }

    private void handleResponse(RevolverHttpResponse response, Response.ResponseBuilder httpResponse, String requestMediaType,
                                Object responseData) throws JsonProcessingException {
        if(responseData == null) {
            httpResponse.entity(response.getBody());
        } else {
            if(requestMediaType.startsWith(MsgPackMediaType.APPLICATION_MSGPACK)) {
                httpResponse.header(HttpHeaders.CONTENT_TYPE, MsgPackMediaType.APPLICATION_MSGPACK);
                httpResponse.entity(msgPackObjectMapper.writeValueAsBytes(responseData));
            } else {
                httpResponse.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
                httpResponse.entity(jsonObjectMapper.writeValueAsBytes(responseData));
            }
        }
    }

    private Object getResponseData(RevolverHttpResponse response, String responseMediaType) throws IOException {
        Object responseData = null;
        if(responseMediaType.startsWith(MediaType.APPLICATION_JSON)) {
            final JsonNode jsonNode = jsonObjectMapper.readTree(response.getBody());
            if(jsonNode.isArray()) {
                responseData = jsonObjectMapper.convertValue(jsonNode, List.class);
            } else {
                responseData = jsonObjectMapper.convertValue(jsonNode, Map.class);
            }
        } else if(responseMediaType.startsWith(MsgPackMediaType.APPLICATION_MSGPACK)) {
            final JsonNode jsonNode = msgPackObjectMapper.readTree(response.getBody());
            if(jsonNode.isArray()) {
                responseData = msgPackObjectMapper.convertValue(jsonNode, List.class);
            } else {
                responseData = msgPackObjectMapper.convertValue(jsonNode, Map.class);
            }
        }
        return responseData;
    }


    private void cleanHeaders(final MultivaluedMap<String, String> headers, RevolverHttpApiConfig apiConfig) {
        headers.remove(HttpHeaders.HOST);
        headers.remove(HttpHeaders.ACCEPT);
        headers.remove(HttpHeaders.ACCEPT_ENCODING);
        headers.putSingle(HttpHeaders.ACCEPT, apiConfig.getAcceptType());
        headers.putSingle(HttpHeaders.ACCEPT_ENCODING, apiConfig.getAcceptEncoding());
    }

    private Response executeCommandAsync(final String service, final RevolverHttpApiConfig api,
                                         final RevolverHttpApiConfig.RequestMethod method, final String path, final HttpHeaders headers,
                                         final UriInfo uriInfo, final byte[] body) throws Exception {

        val requestId = getRequestId(headers);
        val mailBoxTtl = getMailBoxTtl(headers);
        //Short circuit if it is a duplicate request
        if(persistenceProvider.exists(requestId)) {
            return getDuplicateRequestResponse(headers);
        }
        saveRequest(service, api, path, headers, uriInfo, body, requestId);
        CompletableFuture<RevolverHttpResponse> response = executeAndGetResponse(service, api, method, path, uriInfo, body, headers);
        //Async Downstream send accept on request path (Still circuit breaker will kick in. Keep circuit breaker
        // aggressive)
        if(api.isAsync()) {
            return handleAsyncCall(api, method, path, headers, requestId, mailBoxTtl, response);
        } else {
            response.thenAcceptAsync(result -> {
                try {
                    handleSyncCall(headers, requestId, mailBoxTtl, result);
                } catch (Exception e) {
                    log.error("Error setting request state for request id: {}", requestId, e);
                }
            });
            RevolverAckMessage revolverAckMessage = RevolverAckMessage.builder()
                    .requestId(requestId)
                    .acceptedAt(Instant.now()
                                        .toEpochMilli())
                    .build();
            return Response.accepted()
                    .entity(ResponseTransformationUtil.transform(revolverAckMessage, headers.getMediaType() ==
                                                                                     null ? MediaType.APPLICATION_JSON : headers
                                                                                             .getMediaType()
                                                                                             .toString(), jsonObjectMapper,
                                                                 msgPackObjectMapper
                                                                ))
                    .build();
        }
    }

    private String getRequestId(HttpHeaders headers) {
        return headers.getHeaderString(RevolversHttpHeaders.REQUEST_ID_HEADER);
    }

    private Response executeCallbackSync(final String service, final RevolverHttpApiConfig api,
                                         final RevolverHttpApiConfig.RequestMethod method, final String path, final HttpHeaders headers,
                                         final UriInfo uriInfo, final byte[] body) throws Exception {
        log.error("Executing in the sync mode, Resolved path : " + path + ", api path : " + api.getPath());
        val requestId = getRequestId(headers);
        val mailBoxTtl = getMailBoxTtl(headers);
        if(persistenceProvider.exists(requestId))
            return getDuplicateRequestResponse(headers);

        saveRequest(service, api, path, headers, uriInfo, body, requestId);
        log.error("Request saved");
        CompletableFuture<RevolverHttpResponse> response = executeAndGetResponse(service, api, method, path, uriInfo, body, headers);
        val result = response.get();
        log.error("Result received : " + result.toString());
        persistenceProvider.setRequestState(requestId, RevolverRequestState.REQUESTED, mailBoxTtl);
        return transform(headers, result, api.getApi(), path, method);
    }

    private Response getDuplicateRequestResponse(HttpHeaders headers) throws IOException {
        return Response.status(Response.Status.NOT_ACCEPTABLE)
                .entity(ResponseTransformationUtil.transform(DUPLICATE_REQUEST_RESPONSE, getMediaType(headers), jsonObjectMapper,
                                                             msgPackObjectMapper
                                                            ))
                .build();
    }

    private CompletableFuture<RevolverHttpResponse> executeAndGetResponse(String service, RevolverHttpApiConfig api,
                                                                          RevolverHttpApiConfig.RequestMethod method, String path,
                                                                          UriInfo uriInfo, byte[] body, HttpHeaders headers) {
        val sanatizedHeaders = new MultivaluedHashMap<String, String>();
        headers.getRequestHeaders()
                .forEach(sanatizedHeaders::put);
        cleanHeaders(sanatizedHeaders, api);
        val requestId = getRequestId(headers);
        val transactionId = headers.getHeaderString(RevolversHttpHeaders.TXN_ID_HEADER);
        val httpCommand = RevolverBundle.getHttpCommand(service, api.getApi());

        return httpCommand.executeAsync(RevolverHttpRequest.builder()
                                                .traceInfo(TraceInfo.builder()
                                                                   .requestId(requestId)
                                                                   .transactionId(transactionId)
                                                                   .timestamp(System.currentTimeMillis())
                                                                   .build())
                                                .api(api.getApi())
                                                .service(service)
                                                .path(path)
                                                .method(method)
                                                .headers(sanatizedHeaders)
                                                .queryParams(uriInfo.getQueryParameters())
                                                .body(body)
                                                .build());
    }

    private void saveRequest(String service, RevolverHttpApiConfig api, String path, HttpHeaders headers, UriInfo uriInfo, byte[] body,
                             String requestId) throws Exception {
        val mailBoxId = headers.getHeaderString(RevolversHttpHeaders.MAILBOX_ID_HEADER);
        val mailBoxTtl = getMailBoxTtl(headers);

        persistenceProvider.saveRequest(requestId, mailBoxId, RevolverCallbackRequest.builder()
                .api(api.getApi())
                .mode(headers.getRequestHeaders()
                              .getFirst(RevolversHttpHeaders.CALL_MODE_HEADER))
                .callbackUri(headers.getRequestHeaders()
                                     .getFirst(RevolversHttpHeaders.CALLBACK_URI_HEADER))
                .method(headers.getRequestHeaders()
                                .getFirst(RevolversHttpHeaders.CALLBACK_METHOD_HEADER))
                .service(service)
                .path(path)
                .headers(headers.getRequestHeaders())
                .queryParams(uriInfo.getQueryParameters())
                .body(body)
                .build(), mailBoxTtl);
    }

    private int getMailBoxTtl(HttpHeaders headers) {
        return headers.getHeaderString(RevolversHttpHeaders.MAILBOX_TTL_HEADER) != null ? Integer.parseInt(
                headers.getHeaderString(RevolversHttpHeaders.MAILBOX_TTL_HEADER)) : -1;
    }

    private void handleSyncCall(HttpHeaders headers, String requestId, int mailBoxTtl, RevolverHttpResponse result) throws Exception {
        if(result.getStatusCode() == Response.Status.ACCEPTED.getStatusCode()) {
            persistenceProvider.setRequestState(requestId, RevolverRequestState.REQUESTED, mailBoxTtl);
        } else if(result.getStatusCode() == Response.Status.OK.getStatusCode()) {
            persistenceProvider.setRequestState(requestId, RevolverRequestState.RESPONDED, mailBoxTtl);
            downstreamResponseHandler.saveResponse(requestId, getResponse(result), headers.getRequestHeaders()
                    .getFirst(RevolversHttpHeaders.CALLBACK_URI_HEADER));
        } else {
            persistenceProvider.setRequestState(requestId, RevolverRequestState.ERROR, mailBoxTtl);
            downstreamResponseHandler.saveResponse(requestId, getResponse(result), headers.getRequestHeaders()
                    .getFirst(RevolversHttpHeaders.CALLBACK_URI_HEADER));
        }
    }

    private Response handleAsyncCall(RevolverHttpApiConfig api, RevolverHttpApiConfig.RequestMethod method, String path,
                                     HttpHeaders headers, String requestId, int mailBoxTtl,
                                     CompletableFuture<RevolverHttpResponse> response) throws Exception {
        val result = response.get();
        if(result.getStatusCode() == Response.Status.ACCEPTED.getStatusCode()) {
            persistenceProvider.setRequestState(requestId, RevolverRequestState.REQUESTED, mailBoxTtl);
        } else {
            persistenceProvider.setRequestState(requestId, RevolverRequestState.RESPONDED, mailBoxTtl);
            downstreamResponseHandler.saveResponse(requestId, getResponse(result), headers.getRequestHeaders()
                    .getFirst(RevolversHttpHeaders.CALLBACK_URI_HEADER));
        }
        return transform(headers, result, api.getApi(), path, method);
    }

    private RevolverCallbackResponse getResponse(RevolverHttpResponse result) {
        return RevolverCallbackResponse.builder()
                .body(result.getBody())
                .headers(result.getHeaders())
                .statusCode(result.getStatusCode())
                .build();
    }


}
