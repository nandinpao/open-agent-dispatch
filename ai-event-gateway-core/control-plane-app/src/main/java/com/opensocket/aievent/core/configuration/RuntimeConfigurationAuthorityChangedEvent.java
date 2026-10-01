package com.opensocket.aievent.core.configuration;

/** Emitted inside the cutover transaction; listener reconciles local authority only AFTER_COMMIT. */
public record RuntimeConfigurationAuthorityChangedEvent(String setKey,String cutoverId) {}
