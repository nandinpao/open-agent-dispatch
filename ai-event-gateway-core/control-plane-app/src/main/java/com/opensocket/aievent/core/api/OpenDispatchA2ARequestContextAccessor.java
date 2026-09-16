package com.opensocket.aievent.core.api;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.a2a.api.A2AApiRequestContext;
import com.opensocket.aievent.core.a2a.api.A2AApiRequestContextAccessor;
import com.opensocket.aievent.core.http.context.OpenDispatchRequestContext;
import com.opensocket.aievent.core.http.context.OpenDispatchRequestContextHolder;
import com.opensocket.aievent.core.iam.security.contract.MachineAuthenticationContext;
import com.opensocket.aievent.core.iam.security.contract.MachineExecutionContext;

/** Host adapter that supplies authenticated OpenDispatch request metadata to a2a-api. */
@Component
public class OpenDispatchA2ARequestContextAccessor implements A2AApiRequestContextAccessor {
    @Override
    public A2AApiRequestContext current() {
        OpenDispatchRequestContext context = OpenDispatchRequestContextHolder.current().orElse(null);
        if (context == null) {
            return null;
        }
        String correlationId = firstNonBlank(context.correlationId(), context.requestId());
        MachineExecutionContext machineExecution = machineExecutionContext();
        return new A2AApiRequestContext(context.tenantId(), context.operatorId(), correlationId, machineExecution);
    }

    private MachineExecutionContext machineExecutionContext() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) return null;
        if (authentication.getPrincipal() instanceof MachineExecutionContext execution) return execution;
        if (authentication.getPrincipal() instanceof MachineAuthenticationContext machine) {
            return MachineExecutionContext.direct(machine);
        }
        return null;
    }

    private static String firstNonBlank(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }
}
