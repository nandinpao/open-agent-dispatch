package com.opensocket.aievent.core.evidence;

/** Provider-neutral payload protection port. Production sensitive payloads fail closed if unavailable. */
public interface EvidencePayloadCryptoPort {
    ProtectedPayload protect(String tenantId, byte[] plaintext);
    void cryptoShred(String tenantId, String keyRef, String keyVersion, String reason);

    record ProtectedPayload(byte[] ciphertext, String keyRef, String keyVersion) {
        public ProtectedPayload {
            ciphertext = ciphertext == null ? new byte[0] : ciphertext.clone();
        }
        @Override public byte[] ciphertext() { return ciphertext.clone(); }
    }
}
