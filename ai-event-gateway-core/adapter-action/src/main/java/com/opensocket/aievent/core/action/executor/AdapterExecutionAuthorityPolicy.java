package com.opensocket.aievent.core.action.executor;

import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.action.AdapterType;

/** Single authority policy for embedded Core execution vs external worker leasing. */
@Component
public class AdapterExecutionAuthorityPolicy {
    public static final String ISSUE_EXTERNAL_WORKER_FORBIDDEN =
            "ISSUE_TRACKING_EXTERNAL_WORKER_FORBIDDEN_CORE_GOVERNED_AUTHORITY";
    public static final String ISSUE_EXECUTOR_NOT_AVAILABLE = "ISSUE_EXECUTOR_NOT_AVAILABLE";
    public static final String CORE_EXECUTION_NOT_AUTHORIZED = "CORE_EXECUTION_NOT_AUTHORIZED";

    private final AdapterActionExecutionProperties properties;

    public AdapterExecutionAuthorityPolicy(AdapterActionExecutionProperties properties) {
        this.properties = properties;
    }

    public AdapterExecutionAuthority authorityFor(AdapterType adapterType) {
        if (adapterType == null) return AdapterExecutionAuthority.DISABLED;
        if (adapterType == AdapterType.ISSUE_TRACKING) {
            if (properties.getIssue().getExecutionAuthority() != AdapterExecutionAuthority.CORE_GOVERNED
                    || !properties.getIssue().isConnectorRuntimeEnabled()) {
                return AdapterExecutionAuthority.DISABLED;
            }
            return AdapterExecutionAuthority.CORE_GOVERNED;
        }
        if (properties.isEmbeddedMode() && properties.isConfiguredEnabled()) {
            return AdapterExecutionAuthority.CORE_GOVERNED;
        }
        if (properties.isExternalMode()) {
            return AdapterExecutionAuthority.EXTERNAL_WORKER;
        }
        return AdapterExecutionAuthority.DISABLED;
    }

    public boolean canCoreExecute(AdapterType adapterType) {
        return authorityFor(adapterType) == AdapterExecutionAuthority.CORE_GOVERNED;
    }

    public boolean canExternalWorkerClaim(AdapterType adapterType) {
        return authorityFor(adapterType) == AdapterExecutionAuthority.EXTERNAL_WORKER;
    }

    public boolean shouldAutoExecuteInCore(AdapterType adapterType) {
        if (!canCoreExecute(adapterType)) return false;
        if (adapterType == AdapterType.ISSUE_TRACKING) {
            return properties.getIssue().isAutoExecutePending();
        }
        return properties.isAutoExecutePending();
    }

    public void requireCoreExecution(AdapterType adapterType) {
        if (canCoreExecute(adapterType)) return;
        if (adapterType == AdapterType.ISSUE_TRACKING) {
            throw new IllegalStateException(ISSUE_EXECUTOR_NOT_AVAILABLE
                    + ": ISSUE_TRACKING requires Core-governed Connector Runtime execution");
        }
        throw new IllegalStateException(CORE_EXECUTION_NOT_AUTHORIZED
                + ": adapterType=" + adapterType + " authority=" + authorityFor(adapterType));
    }

    public void requireExternalWorkerClaim(AdapterType adapterType) {
        if (canExternalWorkerClaim(adapterType)) return;
        if (adapterType == AdapterType.ISSUE_TRACKING) {
            throw new IllegalStateException(ISSUE_EXTERNAL_WORKER_FORBIDDEN
                    + ": ISSUE_TRACKING is executed only by the Core-governed Connector Runtime");
        }
        throw new IllegalStateException("ADAPTER_EXTERNAL_WORKER_CLAIM_NOT_AUTHORIZED: adapterType="
                + adapterType + " authority=" + authorityFor(adapterType));
    }
}
