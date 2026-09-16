package com.opensocket.aievent.core.a2a.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import com.opensocket.aievent.core.a2a.A2AApprovalMode;
import com.opensocket.aievent.core.a2a.A2ARequestCommand;

/** Pure canonicalization used by A2A idempotency and approval calculations. */
final class A2ARequestCanonicalizer {
    private A2ARequestCanonicalizer() {
    }

    static String requestHash(A2ARequestCommand command) {
        List<String> capabilities = command.requestedCapabilityCodes() == null
                ? List.of()
                : command.requestedCapabilityCodes().stream()
                        .filter(value -> value != null && !value.isBlank())
                        .map(String::trim)
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .toList();
        return operationHash(
                command.tenantId(),
                command.sourceTaskId(),
                command.requestingAgentId(),
                command.requesterType(),
                command.targetDepartmentId(),
                command.targetGroupId(),
                command.targetDomainId(),
                command.requestedTaskType(),
                command.requestedServiceCode(),
                capabilities,
                command.reason(),
                command.inputPayloadRef(),
                command.sensitivityLevel());
    }

    static String operationHash(Object... values) {
        List<String> canonicalValues = new ArrayList<>();
        if (values != null) {
            for (Object value : values) {
                canonicalValues.add(value == null ? "" : String.valueOf(value).trim());
            }
        }
        String canonical = String.join("\u001f", canonicalValues);
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    static int requiredApprovals(A2AApprovalMode approvalMode) {
        if (approvalMode == null || approvalMode == A2AApprovalMode.NONE) {
            return 0;
        }
        return approvalMode == A2AApprovalMode.DUAL_APPROVAL ? 2 : 1;
    }
}
