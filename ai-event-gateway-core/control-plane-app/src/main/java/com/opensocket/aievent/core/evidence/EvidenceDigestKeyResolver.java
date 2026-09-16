package com.opensocket.aievent.core.evidence;

import java.util.Arrays;

/** Adapter port to a tenant-scoped Secrets backend. Secret bytes must never be persisted or logged. */
public interface EvidenceDigestKeyResolver {
    ResolvedEvidenceDigestKey resolve(String tenantId, String keyRef, String keyVersion);

    record ResolvedEvidenceDigestKey(byte[] keyBytes) implements AutoCloseable {
        public ResolvedEvidenceDigestKey {
            keyBytes = keyBytes == null ? new byte[0] : keyBytes.clone();
        }
        @Override public byte[] keyBytes() { return keyBytes.clone(); }
        @Override public void close() { Arrays.fill(keyBytes, (byte) 0); }
    }
}
