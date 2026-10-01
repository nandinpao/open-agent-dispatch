package com.opensocket.aievent.gateway.netty;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Canonical environment/profile compatibility guard for the Gateway process. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GatewayEnvironmentProfileAuthorityGuard implements ApplicationRunner {
    private static final Map<String,String> ENV_PROFILES=Map.of("prod","PRD","uat","UAT","sit","SIT","qa","QA","dev","DEV","local","LOCAL");
    private final Environment springEnvironment; private final String configuredEnvironment; private final boolean enabled;
    public GatewayEnvironmentProfileAuthorityGuard(Environment springEnvironment,
            @Value("${opendispatch.environment:${OPENDISPATCH_ENVIRONMENT:LOCAL}}") String configuredEnvironment,
            @Value("${opendispatch.environment-profile-validation-enabled:false}") boolean enabled){
        this.springEnvironment=springEnvironment;this.configuredEnvironment=configuredEnvironment;this.enabled=enabled;
    }
    @Override public void run(ApplicationArguments args){
        if(!enabled)return;
        String canonical=canonical(configuredEnvironment);
        List<String> profiles=Arrays.stream(springEnvironment.getActiveProfiles()).filter(p->ENV_PROFILES.containsKey(p.toLowerCase(Locale.ROOT))).toList();
        if(profiles.size()!=1)throw new IllegalStateException("OPENDISPATCH_ENVIRONMENT_PROFILE_MISMATCH expected exactly one environment Spring profile for "+canonical+" activeProfiles="+Arrays.toString(springEnvironment.getActiveProfiles()));
        String mapped=ENV_PROFILES.get(profiles.get(0).toLowerCase(Locale.ROOT));
        if(!canonical.equals(mapped))throw new IllegalStateException("OPENDISPATCH_ENVIRONMENT_PROFILE_MISMATCH canonical="+canonical+" profile="+profiles.get(0)+" mappedEnvironment="+mapped);
    }
    private static String canonical(String value){String v=value==null?"":value.trim().toUpperCase(Locale.ROOT);if(!List.of("PRD","UAT","SIT","QA","DEV","LOCAL").contains(v))throw new IllegalStateException("OPENDISPATCH_ENVIRONMENT must be one of PRD,UAT,SIT,QA,DEV,LOCAL: "+value);return v;}
}
