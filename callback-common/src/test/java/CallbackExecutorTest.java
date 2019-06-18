import com.google.inject.Guice;
import com.google.inject.Injector;
import com.platform.callback.common.config.CallbackConfig;
import com.platform.callback.common.executor.CallbackExecutor;
import com.platform.callback.common.executor.CallbackExecutorFactory;
import com.platform.callback.common.guice.ExecutorInjectorModule;
import com.platform.callback.common.handler.InlineCallbackHandler;
import com.platform.callback.common.utils.ConstantUtils;
import io.dropwizard.revolver.core.config.RevolverConfig;
import io.dropwizard.revolver.persistence.InMemoryPersistenceProvider;
import org.junit.Assert;
import org.junit.Test;

/***
 Created by nitish.goyal on 13/06/19
 ***/
public class CallbackExecutorTest {

    private static final InlineCallbackHandler callbackHandler;
    private static final CallbackConfig callbackConfig;
    private static final InMemoryPersistenceProvider inMemoryPersistenceProvider = new InMemoryPersistenceProvider();
    private static final RevolverConfig revolverConfig;

    static {

        revolverConfig = ConstantUtils.getRevolverConfig();
        callbackHandler = InlineCallbackHandler.builder()
                .persistenceProvider(inMemoryPersistenceProvider)
                .revolverConfig(revolverConfig)
                .build();

        callbackConfig = CallbackConfig.builder()
                .callbackType(CallbackConfig.CallbackType.INLINE)
                .build();
    }

    @Test
    public void testFactoryForInlineExecutor() {
        Injector injector = Guice.createInjector(new ExecutorInjectorModule(callbackHandler, callbackConfig, inMemoryPersistenceProvider));
        CallbackExecutorFactory callbackExecutorFactory = new CallbackExecutorFactory(injector);
        CallbackExecutor callbackExecutor = callbackExecutorFactory.getExecutor(callbackConfig.getCallbackType());
        Assert.assertNotNull(callbackExecutor);

    }

    @Test
    public void testFactoryForRmqExecutor() {
        Injector injector = Guice.createInjector(new ExecutorInjectorModule(callbackHandler, callbackConfig, inMemoryPersistenceProvider));
        CallbackExecutorFactory callbackExecutorFactory = new CallbackExecutorFactory(injector);
        CallbackExecutor callbackExecutor = callbackExecutorFactory.getExecutor(callbackConfig.getCallbackType());
        Assert.assertNotNull(callbackExecutor);

    }
}
