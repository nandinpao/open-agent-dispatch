package com.opensocket.aievent.core.iam.token.application.command;public record RevokeAccessTokenCommand(String tenantId,String tokenId,String reason,String actorId,String correlationId){}
