package com.opensocket.aievent.core.configuration;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.kernel.configuration.OpenDispatchEnvironment;

/** Fail-closed validation that the canonical environment and environment Spring profile agree. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OpenDispatchEnvironmentProfileAuthorityGuard implements ApplicationRunner {
    private final Environment springEnvironment;
    private final String configuredEnvironment;
    private final boolean enabled;

    public OpenDispatchEnvironmentProfileAuthorityGuard(Environment springEnvironment,
            @Value("${opendispatch.environment}") String configuredEnvironment,
            @Value("${opendispatch.environment-profile-validation-enabled:false}") boolean enabled) {
        this.springEnvironment=springEnvironment; this.configuredEnvironment=configuredEnvironment; this.enabled=enabled;
    }

    @Override public void run(ApplicationArguments args){
        if(!enabled) return;
        OpenDispatchEnvironment canonical=OpenDispatchEnvironment.parseCanonical(configuredEnvironment);
        List<String> environmentProfiles=Arrays.stream(springEnvironment.getActiveProfiles())
                .filter(OpenDispatchEnvironment::isEnvironmentSpringProfile).toList();
        if(environmentProfiles.size()!=1) throw new IllegalStateException(
                "OPENDISPATCH_ENVIRONMENT_PROFILE_MISMATCH expected exactly one environment Spring profile for "+canonical+
                " activeProfiles="+Arrays.toString(springEnvironment.getActiveProfiles()));
        OpenDispatchEnvironment profileEnvironment=OpenDispatchEnvironment.fromEnvironmentSpringProfile(environmentProfiles.get(0));
        if(canonical!=profileEnvironment) throw new IllegalStateException(
                "OPENDISPATCH_ENVIRONMENT_PROFILE_MISMATCH canonical="+canonical+" profile="+environmentProfiles.get(0)+
                " mappedEnvironment="+profileEnvironment);
    }
}
