package com.opensocket.aievent.core.http.observation;

import io.micrometer.observation.ObservationRegistry;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.ServerRequestObservationConvention;

import com.opensocket.aievent.core.http.context.OpenDispatchRequestContextFilter;

@Configuration
public class OpenDispatchHttpObservationConfiguration {
    @Bean
    public ServerRequestObservationConvention openDispatchServerRequestObservationConvention() {
        return new OpenDispatchServerRequestObservationConvention();
    }

    @Bean
    public OpenDispatchRequestContextFilter openDispatchRequestContextFilter(ObservationRegistry observationRegistry) {
        return new OpenDispatchRequestContextFilter(observationRegistry);
    }

    /** The filter is inserted explicitly into Spring Security after authentication. */
    @Bean
    public FilterRegistrationBean<OpenDispatchRequestContextFilter> disableOpenDispatchRequestContextServletRegistration(
            OpenDispatchRequestContextFilter filter) {
        FilterRegistrationBean<OpenDispatchRequestContextFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
