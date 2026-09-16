package com.opensocket.aievent.core.a2a.api;

/** Decouples the A2A HTTP adapter from the host application's ThreadLocal implementation. */
public interface A2AApiRequestContextAccessor {
    A2AApiRequestContext current();
}
