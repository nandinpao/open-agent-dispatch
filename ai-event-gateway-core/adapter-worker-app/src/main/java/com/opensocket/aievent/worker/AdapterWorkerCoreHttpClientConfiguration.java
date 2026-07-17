package com.opensocket.aievent.worker;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration(proxyBeanMethods = false)
class AdapterWorkerCoreHttpClientConfiguration {

    @Bean("adapterWorkerCoreJdkHttpClient")
    HttpClient adapterWorkerCoreJdkHttpClient(AdapterWorkerProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(properties.getRequestTimeout())
                .build();
    }

    @Bean("adapterWorkerCoreRestClient")
    RestClient adapterWorkerCoreRestClient(
            RestClient.Builder builder,
            @Qualifier("adapterWorkerCoreJdkHttpClient") HttpClient httpClient,
            AdapterWorkerProperties properties
    ) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getRequestTimeout());
        return builder.clone()
                .baseUrl(properties.getCoreBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
