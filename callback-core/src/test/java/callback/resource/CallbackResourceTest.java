package callback.resource;

import callback.BaseCallbackTest;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.platform.callback.core.resources.CallbackResource;
import io.dropwizard.revolver.http.RevolverHttpCommand;
import io.dropwizard.revolver.http.RevolversHttpHeaders;
import io.dropwizard.testing.junit.ResourceTestRule;
import org.glassfish.jersey.internal.MapPropertiesDelegate;
import org.glassfish.jersey.server.ContainerRequest;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.Response;
import java.net.URI;


public class CallbackResourceTest extends BaseCallbackTest {

    @ClassRule
    public static final ResourceTestRule resources = ResourceTestRule.builder()
            .addResource(new CallbackResource(inMemoryPersistenceProvider, callbackHandler, downstreamResponseHandler))
            .build();

    private CallbackResource callbackResource = new CallbackResource(inMemoryPersistenceProvider, callbackHandler,
                                                                     downstreamResponseHandler
    );

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(9999);

    private static final String REQUEST_ID = "r1";
    private static final String PATH = "/revolver/v1/callback/r1";

    @Test
    public void testHandleCallback() throws Exception {

        HttpServletRequest request = new TestHttpServletRequest();

        MapPropertiesDelegate mapPropertiesDelegate = new MapPropertiesDelegate();
        mapPropertiesDelegate.setProperty(RevolversHttpHeaders.CALLBACK_URI_HEADER, "http://localhost:8080/v1/test/async");
        mapPropertiesDelegate.setProperty(RevolversHttpHeaders.CALL_MODE_HEADER, RevolverHttpCommand.CALL_MODE_CALLBACK);
        mapPropertiesDelegate.setProperty(RevolversHttpHeaders.REQUEST_ID_HEADER, REQUEST_ID);


        Response response = callbackResource.handleCallback(REQUEST_ID, "202",
                                                            new ContainerRequest(new URI(PATH), new URI(PATH), HttpMethod.GET, null,
                                                                                 mapPropertiesDelegate
                                                            ), request
                                                           );
        Assert.assertEquals(500, response.getStatus());

    }

}
