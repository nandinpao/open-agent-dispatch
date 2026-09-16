package com.opensocket.aievent.core.iam.api.application.port;
import java.time.Instant;
/** Out-of-band secret delivery boundary. Implementations must never log the secret. */
public interface IamOneTimeSecretDeliveryPort {void deliver(String purpose,String recipientReference,String secret,Instant expiresAt,String correlationId);}
