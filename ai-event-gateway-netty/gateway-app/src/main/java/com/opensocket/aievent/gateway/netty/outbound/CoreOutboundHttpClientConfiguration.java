package com.opensocket.aievent.gateway.netty.outbound;

import com.opensocket.aievent.gateway.netty.config.CoreOutboundProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

/** Builds the observable Spring-managed HTTP client used by the bounded Core dispatcher. */
@Configuration(proxyBeanMethods = false)
public class CoreOutboundHttpClientConfiguration {

    @Bean("coreOutboundJdkHttpClient")
    HttpClient coreOutboundJdkHttpClient(CoreOutboundProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
    }

    @Bean("coreOutboundRestClient")
    RestClient coreOutboundRestClient(
            RestClient.Builder builder,
            @Qualifier("coreOutboundJdkHttpClient") HttpClient httpClient,
            CoreOutboundProperties properties
    ) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.requestTimeout());
        return builder.clone()
                .requestFactory(requestFactory)
                .build();
    }
}
