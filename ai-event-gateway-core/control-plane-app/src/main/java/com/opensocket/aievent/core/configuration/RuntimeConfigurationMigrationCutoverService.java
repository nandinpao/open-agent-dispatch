package com.opensocket.aievent.core.configuration;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.stereotype.Service;

import com.opensocket.aievent.core.action.executor.AdapterExecutorCircuitBreakerRuntimeKeys;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationAuthorityRegistry;

/**
 * Compatibility facade for the V40-9D Adapter Executor pilot route.
 *
 * <p>V41 C3R3-A makes this Config Set wave-managed. The legacy status route remains readable,
 * but mutation routes are intentionally blocked so operators cannot bypass ordered wave governance.
 * Use the C3R3 cutover-wave API instead.</p>
 */
@Service
@Deprecated(forRemoval=false)
public class RuntimeConfigurationMigrationCutoverService {
    private static final String SET_KEY=AdapterExecutorCircuitBreakerRuntimeKeys.SET_KEY;
    private final RuntimeConfigurationGenericCutoverService generic;
    private final RuntimeConfigurationAuthorityRegistry authority;

    public RuntimeConfigurationMigrationCutoverService(RuntimeConfigurationGenericCutoverService generic,
            RuntimeConfigurationAuthorityRegistry authority){this.generic=generic;this.authority=authority;}

    public CutoverPreparation prepareAdapterExecutorCircuitBreaker(String actor,String reason,String correlationId){
        throw new IllegalStateException("CONFIGURATION_CUTOVER_WAVE_MANAGED_USE_WAVE_API setKey="+SET_KEY+" waveId=C3R3-W2");
    }

    public CutoverFinalization finalizeAdapterExecutorCircuitBreaker(String actor,String reason,String correlationId){
        throw new IllegalStateException("CONFIGURATION_CUTOVER_WAVE_MANAGED_USE_WAVE_API setKey="+SET_KEY+" waveId=C3R3-W2");
    }

    public CutoverStatus adapterExecutorCircuitBreakerStatus(){
        var status=generic.status(SET_KEY);
        var keys=status.keys().stream().map(k->new KeyGovernance(k.key(),k.governanceStatus(),authority.isRuntimeAuthoritative(k.key()))).toList();
        boolean complete=!status.blockers().stream().anyMatch(v->v.startsWith("ACTIVE_REVISION_INCOMPLETE"));
        boolean localSnapshot=status.nodes().stream().anyMatch(n->"CORE".equals(n.nodeRole())&&n.converged());
        return new CutoverStatus(SET_KEY,status.environment(),status.activeRevisionId(),complete,localSnapshot,
                localSnapshot?status.activeRevisionId():null,keys);
    }

    public record CutoverPreparation(String setKey,String revisionId,String expectedBaseRevisionId,String status,List<String> keys){}
    public record CutoverFinalization(String setKey,String activeRevisionId,String status,boolean localAuthorityActivated,List<String> keys,OffsetDateTime finalizedAt){}
    public record KeyGovernance(String key,String governanceStatus,boolean localRuntimeAuthority){}
    public record CutoverStatus(String setKey,String environment,String activeRevisionId,boolean activeRevisionComplete,
            boolean localSnapshotPresent,String localSnapshotRevisionId,List<KeyGovernance> keys){}
}
