package com.opensocket.aievent.core.a2a.application.service;

import com.opensocket.aievent.core.a2a.A2ACutoverRepository;
import com.opensocket.aievent.core.a2a.A2ACutoverStage;

/** Blocks every compatibility mutation after the authoritative cutover disables legacy writes. */
public final class A2ALegacyWriteGuard {
    private final A2ACutoverRepository repository;

    public A2ALegacyWriteGuard(A2ACutoverRepository repository) {
        this.repository = repository;
    }

    public void requireAllowed(String entrypoint) {
        A2ACutoverStage stage = repository.find("INSTANCE")
                .map(value -> value.getStage() == null ? A2ACutoverStage.EXPAND : value.getStage())
                .orElse(A2ACutoverStage.EXPAND);
        if (!stage.legacyWritesAllowed()) {
            throw new IllegalStateException("LEGACY_A2A_WRITE_DISABLED:" + entrypoint + ":" + stage.name());
        }
    }
}
