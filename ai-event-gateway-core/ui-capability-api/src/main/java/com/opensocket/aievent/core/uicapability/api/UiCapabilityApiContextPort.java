package com.opensocket.aievent.core.uicapability.api;

/** Implemented by the authenticated control-plane runtime, never by request headers or payloads. */
public interface UiCapabilityApiContextPort { UiCapabilityApiContext current(); }
