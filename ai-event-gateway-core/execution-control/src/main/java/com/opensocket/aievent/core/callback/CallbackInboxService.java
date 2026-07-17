package com.opensocket.aievent.core.callback;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;

/**
 * Durable callback inbox query service.
 *
 * <p>The current storage implementation is the persisted {@link TaskCallbackRepository}. This
 * service gives the rest of the system an explicit callback-inbox boundary: Gateway nodes may
 * receive and relay callbacks, but callback processing truth is queried from Core persisted rows.</p>
 */
@Service
public class CallbackInboxService {
    private final TaskCallbackRepository callbackRepository;

    public CallbackInboxService(TaskCallbackRepository callbackRepository) {
        this.callbackRepository = callbackRepository;
    }

    public List<CallbackInboxEntry> findByTaskId(String taskId, int limit) {
        return callbackRepository.findByTaskId(taskId, safeLimit(limit)).stream()
                .sorted(callbackRecordComparator())
                .map(CallbackInboxEntry::from)
                .toList();
    }

    public List<CallbackInboxEntry> findByDispatchRequestId(String dispatchRequestId, int limit) {
        if (dispatchRequestId == null || dispatchRequestId.isBlank()) {
            return List.of();
        }
        return callbackRepository.findByDispatchRequestId(dispatchRequestId, safeLimit(limit)).stream()
                .sorted(callbackRecordComparator())
                .map(CallbackInboxEntry::from)
                .toList();
    }

    public List<CallbackInboxEntry> recent(int limit) {
        return callbackRepository.recent(safeLimit(limit)).stream()
                .sorted(callbackRecordComparator())
                .map(CallbackInboxEntry::from)
                .toList();
    }

    public CallbackInboxSummary summarizeTask(String taskId, int limit) {
        return summarize(taskId, null, findByTaskId(taskId, limit));
    }

    public CallbackInboxSummary summarizeDispatchRequest(String dispatchRequestId, int limit) {
        return summarize(null, dispatchRequestId, findByDispatchRequestId(dispatchRequestId, limit));
    }

    private CallbackInboxSummary summarize(String taskId, String dispatchRequestId, List<CallbackInboxEntry> entries) {
        CallbackInboxSummary summary = new CallbackInboxSummary();
        summary.setTaskId(firstNonBlank(taskId, entries.stream().map(CallbackInboxEntry::getTaskId).filter(Objects::nonNull).findFirst().orElse(null)));
        summary.setDispatchRequestId(firstNonBlank(dispatchRequestId, entries.stream().map(CallbackInboxEntry::getDispatchRequestId).filter(Objects::nonNull).findFirst().orElse(null)));
        summary.setTotalCallbacks(entries.size());
        summary.setAcceptedCallbacks((int) entries.stream().filter(CallbackInboxEntry::isAccepted).count());
        summary.setRejectedCallbacks((int) entries.stream().filter(entry -> !entry.isAccepted()).count());
        summary.setDuplicateCallbacks((int) entries.stream().filter(CallbackInboxEntry::isDuplicate).count());
        summary.setReplayRejectedCallbacks((int) entries.stream().filter(CallbackInboxEntry::isReplayDetected).count());
        entries.stream().findFirst().ifPresent(latest -> {
            summary.setLatestCallbackId(latest.getCallbackId());
            summary.setLatestCallbackType(latest.getCallbackType());
            summary.setLatestProcessStatus(latest.getProcessStatus());
        });
        boolean terminal = entries.stream().anyMatch(entry -> entry.isAccepted() && isTerminal(entry.getCallbackType()));
        summary.setTerminalCallbackReceived(terminal);
        boolean recoveryRequired = entries.stream().anyMatch(entry -> !entry.isAccepted() || entry.isReplayDetected());
        summary.setRecoveryRequired(recoveryRequired);
        summary.setNextAction(nextAction(entries, terminal, recoveryRequired));
        return summary;
    }

    private String nextAction(List<CallbackInboxEntry> entries, boolean terminal, boolean recoveryRequired) {
        if (recoveryRequired) return "INSPECT_CALLBACK_REJECTION";
        if (terminal) return "NONE";
        if (entries.stream().anyMatch(entry -> "ACK".equals(entry.getCallbackType()) || "PROGRESS".equals(entry.getCallbackType()))) {
            return "WAIT_FOR_AGENT_RESULT";
        }
        return "WAIT_FOR_CALLBACK";
    }

    private boolean isTerminal(String callbackType) {
        return "RESULT".equals(callbackType) || "ERROR".equals(callbackType);
    }

    private Comparator<TaskCallbackRecord> callbackRecordComparator() {
        return Comparator.comparing((TaskCallbackRecord record) -> firstNonNull(record.getProcessedAt(), record.getOccurredAt()), Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private int safeLimit(int limit) {
        return Math.max(1, Math.min(limit <= 0 ? 100 : limit, 500));
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        if (values == null) return null;
        for (T value : values) {
            if (value != null) return value;
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }
}
