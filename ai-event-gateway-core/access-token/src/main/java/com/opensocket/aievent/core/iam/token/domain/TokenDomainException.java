package com.opensocket.aievent.core.iam.token.domain;
public final class TokenDomainException extends RuntimeException {
 private final TokenReasonCode reasonCode;
 public TokenDomainException(TokenReasonCode reasonCode,String message){super(message);this.reasonCode=reasonCode;}
 public TokenReasonCode reasonCode(){return reasonCode;}
}
