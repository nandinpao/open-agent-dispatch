package com.opensocket.aievent.core.a2a.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import com.opensocket.aievent.core.a2a.*;

/** Creates and verifies the immutable directional policy binding for an A2A Request. */
public final class A2APolicySnapshotFactory {

    public A2ADirectionalPolicySnapshot create(
            A2APolicy policy,
            String sourceDomainId,
            String targetDomainId,
            String taskType,
            String serviceCode,
            List<String> requestedCapabilities) {
        Objects.requireNonNull(policy, "policy is required");
        List<String> capabilities = normalizedCapabilities(requestedCapabilities);
        String hash = hash(
                policy.getPolicyId(),
                policy.getVersion(),
                sourceDomainId,
                targetDomainId,
                taskType,
                normalize(serviceCode),
                String.join(",", capabilities),
                policy.getTargetAgentPoolId(),
                enumName(policy.getApprovalMode()),
                policy.getMaxHopCount(),
                policy.getTimeoutSeconds(),
                enumName(policy.getResultAggregationPolicy()),
                policy.getAggregationQuorum(),
                enumName(policy.getCancellationPolicy()),
                enumName(policy.getFailurePropagationPolicy()),
                policy.getHandoffContextPolicyId(),
                policy.getHandoffContextRequirement(),
                policy.getIssueProjectionPolicy());
        return new A2ADirectionalPolicySnapshot(
                policy.getPolicyId(),
                policy.getVersion(),
                sourceDomainId,
                targetDomainId,
                taskType,
                normalize(serviceCode),
                capabilities,
                policy.getTargetAgentPoolId(),
                policy.getApprovalMode(),
                policy.getMaxHopCount(),
                policy.getTimeoutSeconds(),
                policy.getResultAggregationPolicy(),
                policy.getAggregationQuorum(),
                policy.getCancellationPolicy(),
                policy.getFailurePropagationPolicy(),
                policy.getHandoffContextPolicyId(),
                policy.getHandoffContextRequirement(),
                policy.getIssueProjectionPolicy(),
                hash);
    }

    public boolean verify(A2ADirectionalPolicySnapshot snapshot) {
        if (snapshot == null || snapshot.snapshotHash() == null || snapshot.snapshotHash().isBlank()) {
            return false;
        }
        String expected = hash(
                snapshot.policyId(),
                snapshot.policyVersion(),
                snapshot.sourceDomainId(),
                snapshot.targetDomainId(),
                snapshot.taskType(),
                normalize(snapshot.serviceCode()),
                String.join(",", normalizedCapabilities(snapshot.agentCapabilityCodes())),
                snapshot.targetAgentPoolId(),
                enumName(snapshot.approvalMode()),
                snapshot.hopLimit(),
                snapshot.timeoutSeconds(),
                enumName(snapshot.aggregationPolicy()),
                snapshot.aggregationQuorum(),
                enumName(snapshot.cancellationPolicy()),
                enumName(snapshot.failurePropagationPolicy()),
                snapshot.handoffPolicyId(),
                snapshot.handoffRequirement(),
                snapshot.issueProjectionPolicy());
        if (expected.equals(snapshot.snapshotHash())) {
            return true;
        }
        // V49 snapshots predate aggregationQuorum. Jackson reads the absent primitive as zero;
        // verify the exact legacy canonical form without weakening new Schema V2 snapshots.
        if (snapshot.aggregationQuorum() <= 0) {
            String legacy = hash(
                    snapshot.policyId(), snapshot.policyVersion(), snapshot.sourceDomainId(),
                    snapshot.targetDomainId(), snapshot.taskType(), normalize(snapshot.serviceCode()),
                    String.join(",", normalizedCapabilities(snapshot.agentCapabilityCodes())),
                    snapshot.targetAgentPoolId(), enumName(snapshot.approvalMode()), snapshot.hopLimit(),
                    snapshot.timeoutSeconds(), enumName(snapshot.aggregationPolicy()),
                    enumName(snapshot.cancellationPolicy()), enumName(snapshot.failurePropagationPolicy()),
                    snapshot.handoffPolicyId(), snapshot.handoffRequirement(), snapshot.issueProjectionPolicy());
            return legacy.equals(snapshot.snapshotHash());
        }
        return false;
    }

    private List<String> normalizedCapabilities(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .sorted(Comparator.comparing(String::toUpperCase))
                .toList();
    }

    private String hash(Object... values) {
        String canonical = java.util.Arrays.stream(values)
                .map(value -> value == null ? "" : String.valueOf(value).trim())
                .reduce((left, right) -> left + "\u001f" + right)
                .orElse("");
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
