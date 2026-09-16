package com.opensocket.aievent.core.integration.issue.webhook;
public record ProviderWebhookReceipt(ProviderWebhookInboxEntry inbox,ExternalIssueObservation observation,
 ExternalIssueObservedState observedState,ExternalIssueConflict conflict,boolean replay) {}
