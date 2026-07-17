package com.opensocket.aievent.core.action.executor.issue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.action.AdapterAction;
import com.opensocket.aievent.core.action.AdapterType;
import com.opensocket.aievent.core.action.executor.AdapterActionExecutionProperties;
import com.opensocket.aievent.core.action.executor.AdapterExecutionOutcome;
import com.opensocket.aievent.core.action.executor.AdapterExecutionResult;

class IssueExecutorProductionBoundaryTest {

    @Test
    void shouldNotFallbackUnknownVendorToMock() {
        AdapterActionExecutionProperties properties = new AdapterActionExecutionProperties();
        IssueVendorResolver resolver = new IssueVendorResolver(properties);
        AdapterAction action = issueAction(Map.of("vendor", "unknown-vendor"));

        assertNull(resolver.resolve(action));
    }

    @Test
    void shouldFailInsteadOfMockSuccessWhenVendorMissing() {
        AdapterActionExecutionProperties properties = new AdapterActionExecutionProperties();
        IssueTrackingAdapterActionExecutor executor = new IssueTrackingAdapterActionExecutor(properties, new IssueVendorResolver(properties));

        AdapterExecutionResult result = executor.execute(issueAction(Map.of()));

        assertFalse(result.isSuccess());
        assertEquals(AdapterExecutionOutcome.PERMANENT_FAILURE, result.getOutcome());
    }

    @Test
    void shouldRejectMockVendorUnlessExplicitlyEnabled() {
        AdapterActionExecutionProperties properties = new AdapterActionExecutionProperties();
        IssueTrackingAdapterActionExecutor executor = new IssueTrackingAdapterActionExecutor(properties, new IssueVendorResolver(properties));

        AdapterExecutionResult result = executor.execute(issueAction(Map.of("vendor", "MOCK")));

        assertFalse(result.isSuccess());
        assertEquals(AdapterExecutionOutcome.PERMANENT_FAILURE, result.getOutcome());
    }

    @Test
    void shouldAllowMockVendorOnlyWithExplicitOptIn() {
        AdapterActionExecutionProperties properties = new AdapterActionExecutionProperties();
        properties.getMock().setEnabled(true);
        IssueTrackingAdapterActionExecutor executor = new IssueTrackingAdapterActionExecutor(properties, new IssueVendorResolver(properties));

        AdapterExecutionResult result = executor.execute(issueAction(Map.of("vendor", "MOCK")));

        assertEquals(AdapterExecutionOutcome.SUCCESS, result.getOutcome());
    }

    private AdapterAction issueAction(Map<String, Object> payload) {
        AdapterAction action = new AdapterAction();
        action.setActionId("action-001");
        action.setIncidentId("incident-001");
        action.setAdapterType(AdapterType.ISSUE_TRACKING);
        action.setPayload(payload);
        return action;
    }
}
