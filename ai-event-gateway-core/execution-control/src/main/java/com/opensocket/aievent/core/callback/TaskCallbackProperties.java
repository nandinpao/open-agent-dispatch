package com.opensocket.aievent.core.callback;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "task.callback")
public class TaskCallbackProperties {
    private String store = "MEMORY";
    private boolean idempotencyEnabled = true;
    private boolean replayProtectionEnabled = true;
    private boolean rejectCallbackIdReplayMismatch = true;
    /**
     * P11.1 strict mode: callback payload must carry the dispatch token issued by Core.
     * Disable only for local smoke tests or legacy gateway migration windows.
     */
    private boolean requireDispatchToken = true;
    private boolean allowMissingDispatchRequestId = false;
    private boolean enforceStateTransition = true;
    private boolean rejectOldAttemptCallbacks = true;
    private boolean requireAttemptNo = true;
    private boolean enforceGatewayAndAgentIdentity = true;
    private boolean enforceAssignmentFencing = true;
    private boolean requireAssignmentIdForFencing = false;
    private boolean requireKnownAssignmentForFencing = false;
    private boolean allowTerminalCallbackOverride = false;
    private int maxRecent = 1000;
    private Recovery recovery = new Recovery();

    public String getStore() { return store; }
    public void setStore(String store) { this.store = store == null ? "MEMORY" : store; }
    public boolean isIdempotencyEnabled() { return idempotencyEnabled; }
    public void setIdempotencyEnabled(boolean idempotencyEnabled) { this.idempotencyEnabled = idempotencyEnabled; }
    public boolean isReplayProtectionEnabled() { return replayProtectionEnabled; }
    public void setReplayProtectionEnabled(boolean replayProtectionEnabled) { this.replayProtectionEnabled = replayProtectionEnabled; }
    public boolean isRejectCallbackIdReplayMismatch() { return rejectCallbackIdReplayMismatch; }
    public void setRejectCallbackIdReplayMismatch(boolean rejectCallbackIdReplayMismatch) { this.rejectCallbackIdReplayMismatch = rejectCallbackIdReplayMismatch; }
    public boolean isRequireDispatchToken() { return requireDispatchToken; }
    public void setRequireDispatchToken(boolean requireDispatchToken) { this.requireDispatchToken = requireDispatchToken; }
    public boolean isAllowMissingDispatchRequestId() { return allowMissingDispatchRequestId; }
    public void setAllowMissingDispatchRequestId(boolean allowMissingDispatchRequestId) { this.allowMissingDispatchRequestId = allowMissingDispatchRequestId; }
    public boolean isEnforceStateTransition() { return enforceStateTransition; }
    public void setEnforceStateTransition(boolean enforceStateTransition) { this.enforceStateTransition = enforceStateTransition; }
    public boolean isRejectOldAttemptCallbacks() { return rejectOldAttemptCallbacks; }
    public void setRejectOldAttemptCallbacks(boolean rejectOldAttemptCallbacks) { this.rejectOldAttemptCallbacks = rejectOldAttemptCallbacks; }
    public boolean isRequireAttemptNo() { return requireAttemptNo; }
    public void setRequireAttemptNo(boolean requireAttemptNo) { this.requireAttemptNo = requireAttemptNo; }
    public boolean isEnforceGatewayAndAgentIdentity() { return enforceGatewayAndAgentIdentity; }
    public void setEnforceGatewayAndAgentIdentity(boolean enforceGatewayAndAgentIdentity) { this.enforceGatewayAndAgentIdentity = enforceGatewayAndAgentIdentity; }
    public boolean isEnforceAssignmentFencing() { return enforceAssignmentFencing; }
    public void setEnforceAssignmentFencing(boolean enforceAssignmentFencing) { this.enforceAssignmentFencing = enforceAssignmentFencing; }
    public boolean isRequireAssignmentIdForFencing() { return requireAssignmentIdForFencing; }
    public void setRequireAssignmentIdForFencing(boolean requireAssignmentIdForFencing) { this.requireAssignmentIdForFencing = requireAssignmentIdForFencing; }
    public boolean isRequireKnownAssignmentForFencing() { return requireKnownAssignmentForFencing; }
    public void setRequireKnownAssignmentForFencing(boolean requireKnownAssignmentForFencing) { this.requireKnownAssignmentForFencing = requireKnownAssignmentForFencing; }
    public boolean isAllowTerminalCallbackOverride() { return allowTerminalCallbackOverride; }
    public void setAllowTerminalCallbackOverride(boolean allowTerminalCallbackOverride) { this.allowTerminalCallbackOverride = allowTerminalCallbackOverride; }
    public int getMaxRecent() { return maxRecent; }
    public void setMaxRecent(int maxRecent) { this.maxRecent = Math.max(1, Math.min(maxRecent, 5000)); }
    public Recovery getRecovery() { return recovery; }
    public void setRecovery(Recovery recovery) { this.recovery = recovery == null ? new Recovery() : recovery; }

    public static class Recovery {
        private boolean timeoutEnabled = true;
        private boolean autoFailTimedOut = true;
        private boolean retryEnabled = true;
        private int maxAttempts = 3;
        private Duration dispatchTimeout = Duration.ofMinutes(10);
        private Duration initialBackoff = Duration.ofSeconds(30);
        private Duration maxBackoff = Duration.ofMinutes(5);
        private int jitterPercent = 20;
        private int maxBatchSize = 100;

        public boolean isTimeoutEnabled() { return timeoutEnabled; }
        public void setTimeoutEnabled(boolean timeoutEnabled) { this.timeoutEnabled = timeoutEnabled; }
        public boolean isAutoFailTimedOut() { return autoFailTimedOut; }
        public void setAutoFailTimedOut(boolean autoFailTimedOut) { this.autoFailTimedOut = autoFailTimedOut; }
        public boolean isRetryEnabled() { return retryEnabled; }
        public void setRetryEnabled(boolean retryEnabled) { this.retryEnabled = retryEnabled; }
        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = Math.max(1, Math.min(maxAttempts, 20)); }
        public Duration getDispatchTimeout() { return dispatchTimeout; }
        public void setDispatchTimeout(Duration dispatchTimeout) { this.dispatchTimeout = dispatchTimeout == null ? Duration.ofMinutes(10) : dispatchTimeout; }
        public Duration getInitialBackoff() { return initialBackoff; }
        public void setInitialBackoff(Duration initialBackoff) { this.initialBackoff = initialBackoff == null ? Duration.ofSeconds(30) : initialBackoff; }
        public Duration getMaxBackoff() { return maxBackoff; }
        public void setMaxBackoff(Duration maxBackoff) { this.maxBackoff = maxBackoff == null ? Duration.ofMinutes(5) : maxBackoff; }
        public int getJitterPercent() { return jitterPercent; }
        public void setJitterPercent(int jitterPercent) { this.jitterPercent = Math.max(0, Math.min(jitterPercent, 100)); }
        public int getMaxBatchSize() { return maxBatchSize; }
        public void setMaxBatchSize(int maxBatchSize) { this.maxBatchSize = Math.max(1, Math.min(maxBatchSize, 1000)); }
    }
}
