package callback;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.platform.callback.core.resources.CallbackRequestResource;
import io.dropwizard.revolver.RevolverBundle;
import io.dropwizard.revolver.base.core.RevolverCallbackResponse;
import io.dropwizard.revolver.http.RevolverHttpCommand;
import io.dropwizard.revolver.http.RevolversHttpHeaders;
import io.dropwizard.testing.junit.ResourceTestRule;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/***
 Created by nitish.goyal on 12/06/19
 ***/
public class DownstreamResponseInLineHandlerTest extends BaseCallbackTest {

    @ClassRule
    public static final ResourceTestRule callbackRequestResources = ResourceTestRule.builder()
            .addResource(new CallbackRequestResource(new ObjectMapper(), RevolverBundle.msgPackObjectMapper, inMemoryPersistenceProvider,
                                                     callbackHandler, downstreamResponseHandler
            ))
            .build();
    private static final String REQUEST_ID = "r1";
    @Rule
    public WireMockRule wireMockRule = new WireMockRule(9999);
    private Map<String, List<String>> headers = Maps.newHashMap();


    @Before
    public void setUp() {

        callbackRequestResources.client()
                .target("/apis/test/v1/test/async")
                .request()
                .header(RevolversHttpHeaders.REQUEST_ID_HEADER, REQUEST_ID)
                .header(RevolversHttpHeaders.TXN_ID_HEADER, UUID.randomUUID()
                        .toString())
                .header(RevolversHttpHeaders.CALLBACK_URI_HEADER, "http://localhost:8080/v1/test/async")
                .header(RevolversHttpHeaders.CALL_MODE_HEADER, RevolverHttpCommand.CALL_MODE_CALLBACK)
                .header(RevolversHttpHeaders.MAILBOX_ID_HEADER, "123")
                .get()
                .getStatus();


        headers.put(RevolversHttpHeaders.CALLBACK_URI_HEADER, Lists.newArrayList("http://localhost:8080/v1/test/async"));
        headers.put(RevolversHttpHeaders.CALL_MODE_HEADER, Lists.newArrayList(RevolverHttpCommand.CALL_MODE_CALLBACK));
        headers.put(RevolversHttpHeaders.REQUEST_ID_HEADER, Lists.newArrayList(REQUEST_ID));
    }

    @Test
    public void testInlineHandler() {
        stubFor(get(urlEqualTo("/v1/test/async")).willReturn(aResponse().withStatus(202)
                                                                     .withHeader("Content-Type", "application/json")));
        stubFor(get(urlEqualTo("http://localhost:8080/v1/test/async")).willReturn(aResponse().withStatus(202)
                                                                                          .withHeader("Content-Type", "application/json")));

        byte[] body = "response".getBytes();
        RevolverCallbackResponse response = RevolverCallbackResponse.builder()
                .headers(headers)
                .statusCode(Response.Status.OK.getStatusCode())
                .body(body)
                .build();
        downstreamResponseHandler.saveResponse(REQUEST_ID, response, "/v1/test/async");
    }


}
