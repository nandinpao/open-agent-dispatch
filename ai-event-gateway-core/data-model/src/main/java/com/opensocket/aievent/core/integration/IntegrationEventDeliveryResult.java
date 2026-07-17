package com.opensocket.aievent.core.integration;
public record IntegrationEventDeliveryResult(int claimed,int delivered,int retryWaiting,int deadLetter) {}
