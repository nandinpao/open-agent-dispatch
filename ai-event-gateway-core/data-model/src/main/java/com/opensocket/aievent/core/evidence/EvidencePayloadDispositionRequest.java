package com.opensocket.aievent.core.evidence;

/** A0-R8 payload-disposition command. Ledger rows remain immutable. */
public record EvidencePayloadDispositionRequest(
        String tenantId,
        String payloadHandle,
        String disposition,
        String reason,
        String legalHoldRef) {}
