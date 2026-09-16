package com.opensocket.aievent.core.a2a.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.opensocket.aievent.core.a2a.A2AParentAggregation;
import com.opensocket.aievent.core.a2a.A2ARequest;
import com.opensocket.aievent.core.a2a.A2ARequestStatus;
import com.opensocket.aievent.core.a2a.A2AResult;
import com.opensocket.aievent.core.a2a.A2AResultAggregationPolicy;
import com.opensocket.aievent.core.a2a.A2AResultStatus;

class A2AParentAggregationCalculatorTest {
    private final A2AParentAggregationCalculator calculator = new A2AParentAggregationCalculator();

    @Test
    void evaluatesAllCanonicalPoliciesDeterministically() {
        assertStatus(A2AResultAggregationPolicy.ALL_SUCCESS, 1,
                List.of(request(A2ARequestStatus.COMPLETED), request(A2ARequestStatus.COMPLETED)),
                List.of(result(A2AResultStatus.SUCCEEDED), result(A2AResultStatus.SUCCEEDED)), "SUCCESS");
        assertStatus(A2AResultAggregationPolicy.ANY_SUCCESS, 1,
                List.of(request(A2ARequestStatus.COMPLETED), request(A2ARequestStatus.RUNNING)),
                List.of(result(A2AResultStatus.SUCCEEDED)), "SUCCESS");
        assertStatus(A2AResultAggregationPolicy.QUORUM, 2,
                List.of(request(A2ARequestStatus.COMPLETED), request(A2ARequestStatus.COMPLETED)),
                List.of(result(A2AResultStatus.SUCCEEDED), result(A2AResultStatus.PARTIAL)), "PARTIAL");
        assertStatus(A2AResultAggregationPolicy.PARTIAL_ALLOWED, 1,
                List.of(request(A2ARequestStatus.COMPLETED), request(A2ARequestStatus.FAILED)),
                List.of(result(A2AResultStatus.SUCCEEDED), result(A2AResultStatus.FAILED)), "PARTIAL");
        A2AParentAggregation manual = calculate(A2AResultAggregationPolicy.MANUAL_DECISION, 1,
                List.of(request(A2ARequestStatus.COMPLETED)), List.of(result(A2AResultStatus.SUCCEEDED)));
        assertEquals("WAIT_HUMAN", manual.getAggregateStatus());
        assertTrue(manual.isManualDecisionRequired());
        assertStatus(A2AResultAggregationPolicy.FAIL_FAST, 1,
                List.of(request(A2ARequestStatus.FAILED), request(A2ARequestStatus.RUNNING)),
                List.of(result(A2AResultStatus.FAILED)), "FAILURE");
    }

    @Test
    void legacyAliasesResolveToCanonicalPolicies() {
        assertEquals(A2AResultAggregationPolicy.ALL_SUCCESS, A2AResultAggregationPolicy.WAIT_ALL.canonical());
        assertEquals(A2AResultAggregationPolicy.ANY_SUCCESS, A2AResultAggregationPolicy.WAIT_ANY.canonical());
        assertEquals(A2AResultAggregationPolicy.MANUAL_DECISION, A2AResultAggregationPolicy.MANUAL_REVIEW.canonical());
        assertEquals(A2AResultAggregationPolicy.PARTIAL_ALLOWED, A2AResultAggregationPolicy.IGNORE_CHILD_FAILURE.canonical());
        assertEquals(A2AResultAggregationPolicy.FAIL_FAST, A2AResultAggregationPolicy.FAIL_ON_ANY_CHILD_FAILURE.canonical());
    }

    @Test
    void confirmed_cancellation_is_counted_as_cancelled_not_failed() {
        A2AParentAggregation aggregation = calculate(A2AResultAggregationPolicy.ALL_SUCCESS, 1,
                List.of(request(A2ARequestStatus.CANCELLED_CONFIRMED)), List.of());
        assertEquals(1, aggregation.getCancelledCount());
        assertEquals(0, aggregation.getFailedCount());
    }

    @Test
    void sameInputsProduceSameComputationHash() {
        List<A2ARequest> requests = List.of(request(A2ARequestStatus.COMPLETED));
        List<A2AResult> results = List.of(result(A2AResultStatus.SUCCEEDED));
        assertEquals(calculate(A2AResultAggregationPolicy.ALL_SUCCESS, 1, requests, results).getComputationHash(),
                calculate(A2AResultAggregationPolicy.ALL_SUCCESS, 1, requests, results).getComputationHash());
    }

    private void assertStatus(A2AResultAggregationPolicy policy, int quorum,
            List<A2ARequest> requests, List<A2AResult> results, String expected) {
        assertEquals(expected, calculate(policy, quorum, requests, results).getAggregateStatus());
    }

    private A2AParentAggregation calculate(A2AResultAggregationPolicy policy, int quorum,
            List<A2ARequest> requests, List<A2AResult> results) {
        return calculator.calculate("tenant-a", "parent-1", policy, quorum, 7L, "policy-hash",
                requests, results, "result-1");
    }

    private A2ARequest request(A2ARequestStatus status) {
        A2ARequest request = new A2ARequest();
        request.setRequestStatus(status);
        return request;
    }

    private A2AResult result(A2AResultStatus status) {
        A2AResult result = new A2AResult();
        result.setResultStatus(status);
        return result;
    }
}
