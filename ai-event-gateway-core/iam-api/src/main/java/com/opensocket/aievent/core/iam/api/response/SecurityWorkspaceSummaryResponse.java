package com.opensocket.aievent.core.iam.api.response;
public record SecurityWorkspaceSummaryResponse(String tenantId,long activeSessions,long sessionsExpiringSoon,long activeServiceAccounts,long serviceAccountsReviewDue,long activeTokens,long tokensExpiringSoon,long dormantTokens,long deniedDecisions24h,long administrativeChanges24h){}
