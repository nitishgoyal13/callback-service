package com.platform.callback.common.utils;

import com.google.common.collect.Lists;
import io.dropwizard.revolver.core.config.*;
import io.dropwizard.revolver.core.config.hystrix.ThreadPoolConfig;
import io.dropwizard.revolver.discovery.ServiceResolverConfig;
import io.dropwizard.revolver.discovery.model.SimpleEndpointSpec;
import io.dropwizard.revolver.http.config.RevolverHttpApiConfig;
import io.dropwizard.revolver.http.config.RevolverHttpServiceConfig;
import io.dropwizard.revolver.http.config.RevolverHttpsServiceConfig;
import lombok.val;

/***
 Created by nitish.goyal on 24/05/19
 ***/
public class ConstantUtils {

    public static final String MESSAGE = "message";
    public static final String BASE_PACKAGE = "com.platform.callback";
    public static final String APIS = "apis/";


    public static RevolverConfig getRevolverConfig() {

        val simpleEndpoint = new SimpleEndpointSpec();
        simpleEndpoint.setHost("localhost");
        simpleEndpoint.setPort(9999);

        val securedEndpoint = new SimpleEndpointSpec();
        securedEndpoint.setHost("localhost");
        securedEndpoint.setPort(9933);

        ThreadPoolConfig threadPoolConfig = ThreadPoolConfig.builder()
                .concurrency(2)
                .dynamicRequestQueueSize(2)
                .threadPoolName("test")
                .timeout(100)
                .build();


        return RevolverConfig.builder()
                .mailBox(InMemoryMailBoxConfig.builder()
                                 .build())
                .serviceResolverConfig(ServiceResolverConfig.builder()
                                               .namespace("test")
                                               .useCurator(false)
                                               .zkConnectionString("localhost:2181")
                                               .build())
                .clientConfig(ClientConfig.builder()
                                      .clientName("test-client")
                                      .build())
                .global(new RuntimeConfig())
                .service(RevolverHttpServiceConfig.builder()
                                 .authEnabled(false)
                                 .connectionPoolSize(1)
                                 .secured(false)
                                 .enpoint(simpleEndpoint)
                                 .service("test")
                                 .type("http")
                                 .threadPoolGroupConfig(ThreadPoolGroupConfig.builder()
                                                                .threadPools(Lists.newArrayList(threadPoolConfig))
                                                                .build())
                                 .api(RevolverHttpApiConfig.configBuilder()
                                              .api("test")
                                              .method(RevolverHttpApiConfig.RequestMethod.GET)
                                              .method(RevolverHttpApiConfig.RequestMethod.POST)
                                              .method(RevolverHttpApiConfig.RequestMethod.DELETE)
                                              .method(RevolverHttpApiConfig.RequestMethod.PATCH)
                                              .method(RevolverHttpApiConfig.RequestMethod.PUT)
                                              .method(RevolverHttpApiConfig.RequestMethod.HEAD)
                                              .method(RevolverHttpApiConfig.RequestMethod.OPTIONS)
                                              .path("{version}/test")
                                              .runtime(HystrixCommandConfig.builder()
                                                               .threadPool(ThreadPoolConfig.builder()
                                                                                   .concurrency(1)
                                                                                   .timeout(2000)
                                                                                   .build())
                                                               .build())
                                              .build())
                                 .api(RevolverHttpApiConfig.configBuilder()
                                              .api("testAsync")
                                              .method(RevolverHttpApiConfig.RequestMethod.GET)
                                              .method(RevolverHttpApiConfig.RequestMethod.POST)
                                              .method(RevolverHttpApiConfig.RequestMethod.DELETE)
                                              .method(RevolverHttpApiConfig.RequestMethod.PATCH)
                                              .method(RevolverHttpApiConfig.RequestMethod.PUT)
                                              .method(RevolverHttpApiConfig.RequestMethod.HEAD)
                                              .method(RevolverHttpApiConfig.RequestMethod.OPTIONS)
                                              .path("{version}/test/async")
                                              .runtime(HystrixCommandConfig.builder()
                                                               .threadPool(ThreadPoolConfig.builder()
                                                                                   .concurrency(1)
                                                                                   .timeout(2000)
                                                                                   .build())
                                                               .build())
                                              .build())
                                 .api(RevolverHttpApiConfig.configBuilder()
                                              .api("test_multi")
                                              .method(RevolverHttpApiConfig.RequestMethod.GET)
                                              .method(RevolverHttpApiConfig.RequestMethod.POST)
                                              .method(RevolverHttpApiConfig.RequestMethod.DELETE)
                                              .method(RevolverHttpApiConfig.RequestMethod.PATCH)
                                              .method(RevolverHttpApiConfig.RequestMethod.PUT)
                                              .method(RevolverHttpApiConfig.RequestMethod.HEAD)
                                              .method(RevolverHttpApiConfig.RequestMethod.OPTIONS)
                                              .path("{version}/test/{operation}")
                                              .runtime(HystrixCommandConfig.builder()
                                                               .threadPool(ThreadPoolConfig.builder()
                                                                                   .concurrency(1)
                                                                                   .timeout(2000)
                                                                                   .build())
                                                               .build())
                                              .build())
                                 .api(RevolverHttpApiConfig.configBuilder()
                                              .api("callback")
                                              .method(RevolverHttpApiConfig.RequestMethod.GET)
                                              .method(RevolverHttpApiConfig.RequestMethod.POST)
                                              .method(RevolverHttpApiConfig.RequestMethod.DELETE)
                                              .method(RevolverHttpApiConfig.RequestMethod.PATCH)
                                              .method(RevolverHttpApiConfig.RequestMethod.PUT)
                                              .method(RevolverHttpApiConfig.RequestMethod.HEAD)
                                              .method(RevolverHttpApiConfig.RequestMethod.OPTIONS)
                                              .path("{version}/test/callback")
                                              .runtime(HystrixCommandConfig.builder()
                                                               .threadPool(ThreadPoolConfig.builder()
                                                                                   .concurrency(1)
                                                                                   .timeout(2000)
                                                                                   .build())
                                                               .build())
                                              .build())
                                 .api(RevolverHttpApiConfig.configBuilder()
                                              .api("test_group_thread_pool")
                                              .method(RevolverHttpApiConfig.RequestMethod.GET)
                                              .method(RevolverHttpApiConfig.RequestMethod.POST)
                                              .method(RevolverHttpApiConfig.RequestMethod.DELETE)
                                              .method(RevolverHttpApiConfig.RequestMethod.PATCH)
                                              .method(RevolverHttpApiConfig.RequestMethod.PUT)
                                              .method(RevolverHttpApiConfig.RequestMethod.HEAD)
                                              .method(RevolverHttpApiConfig.RequestMethod.OPTIONS)
                                              .path("{version}/test/{operation}")
                                              .runtime(HystrixCommandConfig.builder()
                                                               .threadPool(ThreadPoolConfig.builder()
                                                                                   .concurrency(1)
                                                                                   .timeout(2000)
                                                                                   .build())
                                                               .build())
                                              .build())
                                 .build())
                .service(RevolverHttpsServiceConfig.builder()
                                 .authEnabled(false)
                                 .connectionPoolSize(1)
                                 .enpoint(securedEndpoint)
                                 .service("test_secured")
                                 .type("https")
                                 .api(RevolverHttpApiConfig.configBuilder()
                                              .api("test")
                                              .method(RevolverHttpApiConfig.RequestMethod.GET)
                                              .method(RevolverHttpApiConfig.RequestMethod.POST)
                                              .method(RevolverHttpApiConfig.RequestMethod.DELETE)
                                              .method(RevolverHttpApiConfig.RequestMethod.PATCH)
                                              .method(RevolverHttpApiConfig.RequestMethod.PUT)
                                              .method(RevolverHttpApiConfig.RequestMethod.HEAD)
                                              .method(RevolverHttpApiConfig.RequestMethod.OPTIONS)
                                              .path("{version}/test")
                                              .runtime(HystrixCommandConfig.builder()
                                                               .threadPool(ThreadPoolConfig.builder()
                                                                                   .concurrency(1)
                                                                                   .timeout(2000)
                                                                                   .build())
                                                               .build())
                                              .build())
                                 .api(RevolverHttpApiConfig.configBuilder()
                                              .api("test_multi")
                                              .method(RevolverHttpApiConfig.RequestMethod.GET)
                                              .method(RevolverHttpApiConfig.RequestMethod.POST)
                                              .method(RevolverHttpApiConfig.RequestMethod.DELETE)
                                              .method(RevolverHttpApiConfig.RequestMethod.PATCH)
                                              .method(RevolverHttpApiConfig.RequestMethod.PUT)
                                              .method(RevolverHttpApiConfig.RequestMethod.HEAD)
                                              .method(RevolverHttpApiConfig.RequestMethod.OPTIONS)
                                              .path("{version}/test/{operation}")
                                              .runtime(HystrixCommandConfig.builder()
                                                               .threadPool(ThreadPoolConfig.builder()
                                                                                   .concurrency(1)
                                                                                   .timeout(2000)
                                                                                   .build())
                                                               .build())
                                              .build())
                                 .build())
                .build();
    }

    private ConstantUtils() {
    }
}
