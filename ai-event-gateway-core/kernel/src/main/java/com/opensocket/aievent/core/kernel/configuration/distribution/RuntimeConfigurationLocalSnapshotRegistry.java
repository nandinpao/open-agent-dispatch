package com.opensocket.aievent.core.kernel.configuration.distribution;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Lock-free local read path. Network/DB work is performed before swap; business readers see one immutable envelope.
 * V40-5 adds canonical setKey indexing so typed business consumers never need database Config Set UUIDs.
 */
public final class RuntimeConfigurationLocalSnapshotRegistry {
    private final Map<String, AtomicReference<RuntimeConfigurationSnapshotEnvelope>> snapshotsById = new ConcurrentHashMap<>();
    private final Map<String, AtomicReference<RuntimeConfigurationSnapshotEnvelope>> snapshotsBySetKey = new ConcurrentHashMap<>();

    public Optional<RuntimeConfigurationSnapshotEnvelope> current(String configSetId) {
        AtomicReference<RuntimeConfigurationSnapshotEnvelope> ref=snapshotsById.get(configSetId);
        return ref == null ? Optional.empty() : Optional.ofNullable(ref.get());
    }

    public Optional<RuntimeConfigurationSnapshotEnvelope> currentBySetKey(String setKey) {
        AtomicReference<RuntimeConfigurationSnapshotEnvelope> ref=snapshotsBySetKey.get(setKey);
        return ref == null ? Optional.empty() : Optional.ofNullable(ref.get());
    }

    public RuntimeConfigurationSnapshotEnvelope atomicSwap(RuntimeConfigurationSnapshotEnvelope next) {
        if (next == null || next.configSetId() == null || next.configSetId().isBlank())
            throw new IllegalArgumentException("snapshot/configSetId is required");
        if (next.setKey() == null || next.setKey().isBlank())
            throw new IllegalArgumentException("snapshot/setKey is required");
        AtomicReference<RuntimeConfigurationSnapshotEnvelope> ref=snapshotsById.computeIfAbsent(next.configSetId(), ignored->new AtomicReference<>());
        while (true) {
            RuntimeConfigurationSnapshotEnvelope current=ref.get();
            if (current != null && current.sequenceNo() > next.sequenceNo()) return current;
            if (ref.compareAndSet(current,next)) {
                snapshotsBySetKey.put(next.setKey(),ref);
                return next;
            }
        }
    }
}
