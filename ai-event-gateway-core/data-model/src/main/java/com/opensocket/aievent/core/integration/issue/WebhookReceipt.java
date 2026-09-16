package com.opensocket.aievent.core.integration.issue;
public record WebhookReceipt(IntegrationInboxEntry inbox,boolean replay,boolean conflict) {}
