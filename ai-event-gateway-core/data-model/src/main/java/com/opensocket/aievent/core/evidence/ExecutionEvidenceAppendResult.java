package com.opensocket.aievent.core.evidence;

/** Immutable identifiers returned after A0-R8 evidence append. */
public record ExecutionEvidenceAppendResult(
        String evidenceId,
        String opaquePayloadHandle,
        String payloadDigest,
        String digestMode,
        String digestKeyVersion,
        String status) {}
