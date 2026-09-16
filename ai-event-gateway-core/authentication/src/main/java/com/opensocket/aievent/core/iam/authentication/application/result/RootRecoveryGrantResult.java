package com.opensocket.aievent.core.iam.authentication.application.result;import java.time.Instant;public record RootRecoveryGrantResult(String grantId,String secret,Instant expiresAt){}
