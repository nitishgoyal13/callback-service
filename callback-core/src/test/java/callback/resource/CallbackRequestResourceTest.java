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

package callback.resource;

import callback.BaseCallbackTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.platform.callback.core.resources.CallbackRequestResource;
import io.dropwizard.revolver.RevolverBundle;
import io.dropwizard.revolver.http.RevolverHttpCommand;
import io.dropwizard.revolver.http.RevolversHttpHeaders;
import io.dropwizard.testing.junit.ResourceTestRule;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import java.util.Collections;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.Assert.assertEquals;

/**
 * @author phaneesh
 */
public class CallbackRequestResourceTest extends BaseCallbackTest {

    @ClassRule
    public static final ResourceTestRule resources = ResourceTestRule.builder()
            .addResource(new CallbackRequestResource(new ObjectMapper(), RevolverBundle.msgPackObjectMapper, inMemoryPersistenceProvider,
                                                     callbackHandler, downstreamResponseHandler
            ))
            .build();

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(9999);

    @Test
    public void testGetRequestInSyncMode() {
        stubFor(get(urlEqualTo("/v1/test")).willReturn(aResponse().withStatus(202)
                                                               .withHeader("Content-Type", "application/json")));
        stubFor(get(urlEqualTo("/v1/test/callback")).willReturn(aResponse().withStatus(200)
                                                                        .withHeader("Content-Type", "application/json")));
        assertEquals(202, resources.client()
                .target("/apis/test/v1/test")
                .request()
                .header(RevolversHttpHeaders.REQUEST_ID_HEADER, UUID.randomUUID()
                        .toString())
                .header(RevolversHttpHeaders.TXN_ID_HEADER, UUID.randomUUID()
                        .toString())
                .header(RevolversHttpHeaders.CALLBACK_URI_HEADER, "/apis/test/v1/test/callback")
                .header(RevolversHttpHeaders.CALL_MODE_HEADER, RevolverHttpCommand.CALL_MODE_CALLBACK)
                .header(RevolversHttpHeaders.MAILBOX_ID_HEADER, "123")
                .get()
                .getStatus());
    }

    @Test
    public void testGetRequestInASyncMode() {
        stubFor(get(urlEqualTo("/v1/test/async")).willReturn(aResponse().withStatus(202)
                                                                     .withHeader("Content-Type", "application/json")));
        stubFor(get(urlEqualTo("/v1/test/callback")).willReturn(aResponse().withStatus(200)
                                                                        .withHeader("Content-Type", "application/json")));
        assertEquals(202, resources.client()
                .target("/apis/test/v1/test/async")
                .request()
                .header(RevolversHttpHeaders.REQUEST_ID_HEADER, UUID.randomUUID()
                        .toString())
                .header(RevolversHttpHeaders.TXN_ID_HEADER, UUID.randomUUID()
                        .toString())
                .header(RevolversHttpHeaders.CALLBACK_URI_HEADER, "/apis/test/v1/test/callback")
                .header(RevolversHttpHeaders.CALL_MODE_HEADER, RevolverHttpCommand.CALL_MODE_CALLBACK)
                .header(RevolversHttpHeaders.MAILBOX_ID_HEADER, "123")
                .get()
                .getStatus());
    }

    @Test
    public void testPostRequest() {
        stubFor(post(urlEqualTo("/v1/test")).willReturn(aResponse().withStatus(202)
                                                                .withHeader("Content-Type", "application/json")));
        stubFor(get(urlEqualTo("/v1/test/callback")).willReturn(aResponse().withStatus(200)
                                                                        .withHeader("Content-Type", "application/json")));

        assertEquals(202, resources.client()
                .target("/apis/test/v1/test")
                .request()
                .header(RevolversHttpHeaders.REQUEST_ID_HEADER, UUID.randomUUID()
                        .toString())
                .header(RevolversHttpHeaders.TXN_ID_HEADER, UUID.randomUUID()
                        .toString())
                .header(RevolversHttpHeaders.CALLBACK_URI_HEADER, "/apis/test/v1/test/callback")
                .header(RevolversHttpHeaders.CALL_MODE_HEADER, RevolverHttpCommand.CALL_MODE_CALLBACK)
                .header(RevolversHttpHeaders.MAILBOX_ID_HEADER, "123")
                .post(null)
                .getStatus());
    }

    @Test
    public void testPutRequest() {
        stubFor(put(urlEqualTo("/v1/test")).willReturn(aResponse().withStatus(202)
                                                               .withHeader("Content-Type", "application/json")));
        stubFor(get(urlEqualTo("/v1/test/callback")).willReturn(aResponse().withStatus(202)
                                                                        .withHeader("Content-Type", "application/json")));
        assertEquals(202, resources.client()
                .target("/apis/test/v1/test")
                .request()
                .header(RevolversHttpHeaders.REQUEST_ID_HEADER, UUID.randomUUID()
                        .toString())
                .header(RevolversHttpHeaders.TXN_ID_HEADER, UUID.randomUUID()
                        .toString())
                .header(RevolversHttpHeaders.CALLBACK_URI_HEADER, "/apis/test/v1/test/callback")
                .header(RevolversHttpHeaders.CALL_MODE_HEADER, RevolverHttpCommand.CALL_MODE_CALLBACK)
                .header(RevolversHttpHeaders.MAILBOX_ID_HEADER, "123")
                .put(Entity.entity(Collections.singletonMap("test", "test"), MediaType.APPLICATION_JSON))
                .getStatus());
    }

    @Test
    public void testDeleteRequest() {
        stubFor(delete(urlEqualTo("/v1/test")).willReturn(aResponse().withStatus(202)
                                                                  .withHeader("Content-Type", "application/json")));
        stubFor(get(urlEqualTo("/v1/test/callback")).willReturn(aResponse().withStatus(202)
                                                                        .withHeader("Content-Type", "application/json")));

        assertEquals(202, resources.client()
                .target("/apis/test/v1/test")
                .request()
                .header(RevolversHttpHeaders.REQUEST_ID_HEADER, UUID.randomUUID()
                        .toString())
                .header(RevolversHttpHeaders.TXN_ID_HEADER, UUID.randomUUID()
                        .toString())
                .header(RevolversHttpHeaders.CALLBACK_URI_HEADER, "/apis/test/v1/test/callback")
                .header(RevolversHttpHeaders.CALL_MODE_HEADER, RevolverHttpCommand.CALL_MODE_CALLBACK)
                .header(RevolversHttpHeaders.MAILBOX_ID_HEADER, "123")
                .delete()
                .getStatus());
    }

    @Test
    public void testHeadRequest() {
        stubFor(head(urlEqualTo("/v1/test")).willReturn(aResponse().withStatus(202)
                                                                .withHeader("Content-Type", "application/json")));
        stubFor(get(urlEqualTo("/v1/test/callback")).willReturn(aResponse().withStatus(200)
                                                                        .withHeader("Content-Type", "application/json")));

        assertEquals(202, resources.client()
                .target("/apis/test/v1/test")
                .request()
                .header(RevolversHttpHeaders.REQUEST_ID_HEADER, UUID.randomUUID()
                        .toString())
                .header(RevolversHttpHeaders.TXN_ID_HEADER, UUID.randomUUID()
                        .toString())
                .header(RevolversHttpHeaders.CALLBACK_URI_HEADER, "/apis/test/v1/test/callback")
                .header(RevolversHttpHeaders.CALL_MODE_HEADER, RevolverHttpCommand.CALL_MODE_CALLBACK)
                .header(RevolversHttpHeaders.MAILBOX_ID_HEADER, "123")
                .head()
                .getStatus());
    }

    @Test
    public void testPatchRequest() {
        stubFor(patch(urlEqualTo("/v1/test")).willReturn(aResponse().withStatus(202)
                                                                 .withHeader("Content-Type", "application/json")));
        stubFor(get(urlEqualTo("/v1/test/callback")).willReturn(aResponse().withStatus(202)
                                                                        .withHeader("Content-Type", "application/json")));

        assertEquals(202, resources.client()
                .target("/apis/test/v1/test")
                .request()
                .header(RevolversHttpHeaders.REQUEST_ID_HEADER, UUID.randomUUID()
                        .toString())
                .header(RevolversHttpHeaders.TXN_ID_HEADER, UUID.randomUUID()
                        .toString())
                .header(RevolversHttpHeaders.CALLBACK_URI_HEADER, "/apis/test/v1/test/callback")
                .header(RevolversHttpHeaders.CALL_MODE_HEADER, RevolverHttpCommand.CALL_MODE_CALLBACK)
                .header(RevolversHttpHeaders.MAILBOX_ID_HEADER, "123")
                .method("PATCH")
                .getStatus());
    }

    @Test
    public void testOptionsRequest() {
        stubFor(options(urlEqualTo("/v1/test")).willReturn(aResponse().withStatus(202)
                                                                   .withHeader("Content-Type", "application/json")));
        stubFor(get(urlEqualTo("/v1/test/callback")).willReturn(aResponse().withStatus(200)
                                                                        .withHeader("Content-Type", "application/json")));

        assertEquals(202, resources.client()
                .target("/apis/test/v1/test")
                .request()
                .header(RevolversHttpHeaders.REQUEST_ID_HEADER, UUID.randomUUID()
                        .toString())
                .header(RevolversHttpHeaders.TXN_ID_HEADER, UUID.randomUUID()
                        .toString())
                .header(RevolversHttpHeaders.CALLBACK_URI_HEADER, "/apis/test/v1/test/callback")
                .header(RevolversHttpHeaders.CALL_MODE_HEADER, RevolverHttpCommand.CALL_MODE_CALLBACK)
                .header(RevolversHttpHeaders.MAILBOX_ID_HEADER, "123")
                .options()
                .getStatus());
    }


}
