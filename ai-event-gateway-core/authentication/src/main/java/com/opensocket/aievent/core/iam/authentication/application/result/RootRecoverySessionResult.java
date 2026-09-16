package com.opensocket.aievent.core.iam.authentication.application.result;
import java.time.Instant;
public record RootRecoverySessionResult(String grantId,String sessionId,Instant expiresAt) {}
