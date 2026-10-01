package com.opensocket.aievent.core.kernel.configuration.distribution;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/** Durable Runtime Configuration distribution, topology-target, and apply-state port. */
public interface RuntimeConfigurationDistributionStore {
    List<String> listActiveConfigSetIds(String environment);
    List<RuntimeConfigurationDistributionOutboxEntry> claimDue(String workerId, int limit, Duration lease);
    void markDistributed(String configSetId, String revisionId, String workerId, String payloadFingerprint);
    void markFailed(String configSetId, String revisionId, String workerId, OffsetDateTime retryAt, String error);
    void markSuperseded(String configSetId, String revisionId, String workerId);
    void requestRedistribution(String configSetId, String revisionId, String reason);

    /** Register topology expectation independently from apply evidence. */
    default void registerRequiredTarget(String configSetId, String nodeId, String nodeRole, String nodeInstanceId, String registrationSource) {
        registerRequiredTarget(configSetId,nodeId,nodeRole,nodeInstanceId,registrationSource,0);
    }
    void registerRequiredTarget(String configSetId, String nodeId, String nodeRole, String nodeInstanceId, String registrationSource,
            int supportedAuthorityContractVersion);
    List<RuntimeConfigurationRequiredNodeTarget> listRequiredTargets(String configSetId);

    /**
     * Returns one row per required target. Targets with no apply evidence are projected as
     * NOT_SEEN and therefore still participate in convergence denominator calculations.
     */
    List<RuntimeConfigurationNodeApplyState> listRequiredApplyStates(String configSetId);

    /** Marks previously observed required targets stale when their real node presence exceeds the threshold. */
    int markStaleRequiredNodes(String environment, Duration staleAfter);

    void recordDesired(String configSetId, String nodeId, String nodeRole, String nodeInstanceId,
            String desiredRevisionId, String snapshotFingerprint, boolean distributed);
    void acknowledgeApplied(String configSetId, String nodeId, String nodeRole, String nodeInstanceId,
            String desiredRevisionId, String appliedRevisionId, String snapshotFingerprint);
    void acknowledgeFailed(String configSetId, String nodeId, String nodeRole, String nodeInstanceId,
            String desiredRevisionId, String appliedRevisionId, String snapshotFingerprint, String errorCode, String errorDetail);
    /** Node-reported local signed-snapshot usability. Telemetry only; never changes desired authority. */
    void recordAuthorityHealth(String configSetId, String nodeId, String nodeRole, String nodeInstanceId,
            String authorityRuntimeState, OffsetDateTime snapshotExpiresAt);
    Optional<RuntimeConfigurationNodeApplyState> findApplyState(String configSetId, String nodeId);
    List<RuntimeConfigurationNodeApplyState> listApplyStates(String configSetId);
}
