package com.opensocket.aievent.core.resourceaccess.contract;
/** Immutable audit evidence for attachment authorization and handle issuance. */
public interface AttachmentAccessAuditPort { void append(AttachmentDownloadGrant grant,String principalId,String purpose,String correlationId); }
