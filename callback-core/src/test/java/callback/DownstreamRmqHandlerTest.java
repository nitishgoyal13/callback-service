package callback;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.platform.callback.common.config.CallbackConfig;
import com.platform.callback.common.executor.CallbackExecutor;
import com.platform.callback.common.executor.CallbackExecutorFactory;
import com.platform.callback.common.guice.ExecutorInjectorModule;
import com.platform.callback.core.resources.CallbackRequestResource;
import com.platform.callback.rmq.RMQActionMessagePublisher;
import com.platform.callback.rmq.actors.messages.CallbackMessage;
import io.dropwizard.revolver.RevolverBundle;
import io.dropwizard.revolver.base.core.RevolverCallbackRequest;
import io.dropwizard.revolver.base.core.RevolverCallbackResponse;
import io.dropwizard.revolver.http.RevolverHttpCommand;
import io.dropwizard.revolver.http.RevolversHttpHeaders;
import io.dropwizard.testing.junit.ResourceTestRule;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.rule.PowerMockRule;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

/***
 Created by nitish.goyal on 12/06/19
 ***/
@RunWith(PowerMockRunner.class)
@PrepareForTest(RMQActionMessagePublisher.class)
public class DownstreamRmqHandlerTest extends BaseCallbackTest {

    @ClassRule
    public static final ResourceTestRule callbackRequestResources = ResourceTestRule.builder()
            .addResource(new CallbackRequestResource(new ObjectMapper(), RevolverBundle.msgPackObjectMapper, inMemoryPersistenceProvider,
                                                     callbackHandler, downstreamResponseHandler
            ))
            .build();

    private static final String REQUEST_ID = "r1";
    private static final String MAILBOX_ID = "m1";

    private byte[] body = "response".getBytes();
    private Map<String, List<String>> headers = Maps.newHashMap();

    @Rule
    public PowerMockRule wireMockRule = new PowerMockRule();


    @Before
    public void setUp() {

        headers.put(RevolversHttpHeaders.CALLBACK_URI_HEADER, Lists.newArrayList("http://localhost:8080/v1/test/async"));
        headers.put(RevolversHttpHeaders.CALL_MODE_HEADER, Lists.newArrayList(RevolverHttpCommand.CALL_MODE_CALLBACK));
        headers.put(RevolversHttpHeaders.REQUEST_ID_HEADER, Lists.newArrayList(REQUEST_ID));
        headers.put(RevolversHttpHeaders.MAILBOX_TTL_HEADER, Lists.newArrayList("7"));

        inMemoryPersistenceProvider.saveRequest(REQUEST_ID, MAILBOX_ID, RevolverCallbackRequest.builder()
                .api("/apis/test/v1/test/async")
                .mode(RevolverHttpCommand.CALL_MODE_CALLBACK)
                .callbackUri("http://localhost:8080/v1/test/async")
                .headers(headers)
                .method("POST")
                .service("test")
                .path("v1/test/async")
                .build(), 7);

    }

    @Test
    public void testRmqHandler() {

        callbackConfig.setCallbackType(CallbackConfig.CallbackType.RMQ);
        Injector injector = Guice.createInjector(new ExecutorInjectorModule(callbackHandler, callbackConfig, inMemoryPersistenceProvider));
        CallbackExecutorFactory callbackExecutorFactory = new CallbackExecutorFactory(injector);
        CallbackExecutor callbackExecutor = callbackExecutorFactory.getExecutor(callbackConfig.getCallbackType());
        callbackExecutor.initialize(appConfig, environment);

        downstreamResponseHandler.setCallbackExecutor(callbackExecutor);

        RevolverCallbackResponse response = RevolverCallbackResponse.builder()
                .headers(headers)
                .statusCode(Response.Status.OK.getStatusCode())
                .body(body)
                .build();

        PowerMockito.mockStatic(RMQActionMessagePublisher.class);
        PowerMockito.when(RMQActionMessagePublisher.publish(ArgumentMatchers.any(CallbackMessage.class)))
                .thenReturn(true);


        System.out.println("String: " + RMQActionMessagePublisher.publish(CallbackMessage.builder()
                                                                                  .build()));
        downstreamResponseHandler.saveResponse(REQUEST_ID, response, "/v1/test/async");
    }
}
