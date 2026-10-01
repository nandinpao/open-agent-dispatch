package com.opensocket.aievent.core.api.contract;

import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationAuthorityRegistry;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSetKeys;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSnapshotValues;

/** V41-C3R2M typed runtime view for API mutation contract policy. */
@Component
public final class ApiContractRuntimeConfigurationView {
    public static final String SET_KEY = RuntimeConfigurationSetKeys.CORE_SYSTEM;
    public static final String ENFORCEMENT = "core.api-contract.enforcement";
    public static final String REQUIRE_IDEMPOTENCY = "core.api-contract.require-idempotency";
    public static final String REQUIRE_CORRELATION_ID = "core.api-contract.require-correlation-id";
    public static final String REQUIRE_EXPECTED_VERSION = "core.api-contract.require-expected-version";
    public static final String REQUIRE_ACTOR_IDENTITY = "core.api-contract.require-actor-identity";
    public static final String REQUIRE_AUDIT_REASON = "core.api-contract.require-audit-reason";
    public static final Set<String> ALL = Set.of(ENFORCEMENT, REQUIRE_IDEMPOTENCY, REQUIRE_CORRELATION_ID, REQUIRE_EXPECTED_VERSION, REQUIRE_ACTOR_IDENTITY, REQUIRE_AUDIT_REASON);

    private final RuntimeConfigurationSnapshotValues values;
    private final RuntimeConfigurationAuthorityRegistry authority;
    private final ApiContractProperties startup;

    @Autowired
    public ApiContractRuntimeConfigurationView(RuntimeConfigurationSnapshotValues values,
                                               RuntimeConfigurationAuthorityRegistry authority,
                                               ApiContractProperties startup) {
        this.values=values; this.authority=authority; this.startup=startup;
    }
    public ApiContractRuntimeConfigurationView(ApiContractProperties startup) {
        this(null,null,startup==null?new ApiContractProperties():startup);
    }

    public ApiContractEnforcementMode enforcement() {
        String raw=textValue(ENFORCEMENT,startup.getEnforcement().name());
        try { return ApiContractEnforcementMode.valueOf(raw.trim().toUpperCase()); }
        catch(RuntimeException ex){ throw invalid(ENFORCEMENT); }
    }
    public boolean requireIdempotency(){return booleanValue(REQUIRE_IDEMPOTENCY,startup.isRequireIdempotency());}
    public boolean requireCorrelationId(){return booleanValue(REQUIRE_CORRELATION_ID,startup.isRequireCorrelationId());}
    public boolean requireExpectedVersion(){return booleanValue(REQUIRE_EXPECTED_VERSION,startup.isRequireExpectedVersion());}
    public boolean requireActorIdentity(){return booleanValue(REQUIRE_ACTOR_IDENTITY,startup.isRequireActorIdentity());}
    public boolean requireAuditReason(){return booleanValue(REQUIRE_AUDIT_REASON,startup.isRequireAuditReason());}
    /** Startup-only path-shape configuration remains deployment/domain configuration. */
    public Set<String> expectedVersionPathFragments(){return Set.copyOf(startup.getExpectedVersionPathFragments());}
    public Set<String> auditReasonExemptPathPrefixes(){return Set.copyOf(startup.getAuditReasonExemptPathPrefixes());}

    private boolean required(String key){return authority!=null&&authority.isRuntimeAuthoritative(key);} private void require(String key){if(values==null||!values.hasSnapshot(SET_KEY))throw incomplete(key);values.requireKeys(SET_KEY,ALL);}
    private boolean booleanValue(String key,boolean fallback){if(values==null)return fallback;if(required(key)){require(key);return values.booleanValue(SET_KEY,key).orElseThrow(()->incomplete(key));}return values.booleanValue(SET_KEY,key).orElse(fallback);}
    private String textValue(String key,String fallback){if(values==null)return fallback;if(required(key)){require(key);return values.textValue(SET_KEY,key).orElseThrow(()->incomplete(key));}return values.textValue(SET_KEY,key).orElse(fallback);}
    private static IllegalStateException invalid(String key){return new IllegalStateException("RUNTIME_CONFIG_VALIDATION_FAILED key="+key);} private static IllegalStateException incomplete(String key){return new IllegalStateException("CONFIGURATION_INCOMPLETE setKey="+SET_KEY+" key="+key);}
}
