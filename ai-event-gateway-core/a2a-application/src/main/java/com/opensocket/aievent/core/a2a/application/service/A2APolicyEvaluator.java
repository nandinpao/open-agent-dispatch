package com.opensocket.aievent.core.a2a.application.service;

import java.util.Comparator;
import java.util.List;

import com.opensocket.aievent.core.a2a.A2APolicy;
import com.opensocket.aievent.core.a2a.A2AReasonCode;
import com.opensocket.aievent.core.a2a.A2ARejectedException;
import com.opensocket.aievent.core.a2a.A2ARequestCommand;

/** Pure policy filtering that leaves persistence and state transitions in Core authority. */
final class A2APolicyEvaluator {
    private A2APolicyEvaluator() {
    }

    static A2APolicy selectPolicy(List<A2APolicy> values, A2ARequestCommand command) {
        List<A2APolicy> taskAllowed = values.stream()
                .filter(policy -> policy.allowsTaskType(command.requestedTaskType()))
                .toList();
        if (taskAllowed.isEmpty()) {
            reject(A2AReasonCode.A2A_TASK_TYPE_NOT_ALLOWED,
                    "No directional Policy allows Task Type " + command.requestedTaskType());
        }
        List<A2APolicy> serviceAllowed = taskAllowed.stream()
                .filter(policy -> policy.allowsServiceCode(command.requestedServiceCode()))
                .toList();
        if (serviceAllowed.isEmpty()) {
            reject(A2AReasonCode.A2A_SERVICE_CODE_NOT_ALLOWED,
                    "No directional Policy allows Service Code " + command.requestedServiceCode());
        }
        return serviceAllowed.stream()
                .filter(policy -> policy.allowsCapabilities(command.requestedCapabilityCodes()))
                .sorted(Comparator.comparing(A2APolicy::getPolicyCode))
                .findFirst()
                .orElseThrow(() -> new A2ARejectedException(
                        A2AReasonCode.A2A_CAPABILITY_NOT_ALLOWED,
                        "Requested capabilities are not allowed."));
    }

    static void validateSensitivity(String requested, String allowed) {
        if (sensitivityRank(firstNonBlank(requested, "INTERNAL"))
                > sensitivityRank(firstNonBlank(allowed, "INTERNAL"))) {
            reject(A2AReasonCode.A2A_SENSITIVITY_EXCEEDED,
                    "Requested sensitivity exceeds Policy maximum.");
        }
    }

    private static int sensitivityRank(String value) {
        return switch (value.toUpperCase()) {
            case "PUBLIC" -> 0;
            case "INTERNAL" -> 1;
            case "CONFIDENTIAL" -> 2;
            case "RESTRICTED" -> 3;
            default -> 4;
        };
    }

    private static String firstNonBlank(String... values) {
        if (values != null) {
            for (String value : values) {
                if (value != null && !value.isBlank()) return value.trim();
            }
        }
        return null;
    }

    private static void reject(A2AReasonCode reasonCode, String message) {
        throw new A2ARejectedException(reasonCode, message);
    }
}
