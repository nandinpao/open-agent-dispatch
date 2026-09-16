package com.opensocket.aievent.core.api.contract;
import org.springframework.boot.context.properties.EnableConfigurationProperties; import org.springframework.context.annotation.Configuration; import org.springframework.web.servlet.config.annotation.*;
@Configuration @EnableConfigurationProperties(ApiContractProperties.class)
public class ApiContractWebConfiguration implements WebMvcConfigurer {private final ApiMutationContractInterceptor interceptor;public ApiContractWebConfiguration(ApiMutationContractInterceptor i){interceptor=i;}@Override public void addInterceptors(InterceptorRegistry registry){registry.addInterceptor(interceptor).addPathPatterns("/api/**").excludePathPatterns("/api/external/provider-webhooks/**");}}
