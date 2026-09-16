package com.opensocket.aievent.core.resourceaccess.contract;
public interface BackgroundAuthorizationAuditPort { void append(BackgroundJobAuthorization authorization,String purpose,String correlationId); }
