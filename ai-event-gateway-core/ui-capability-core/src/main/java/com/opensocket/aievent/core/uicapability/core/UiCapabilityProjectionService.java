package com.opensocket.aievent.core.uicapability.core;

import com.opensocket.aievent.core.uicapability.contract.UiCapabilityEnvelope;

public interface UiCapabilityProjectionService {
    UiCapabilityEnvelope project(UiCapabilityProjectionCommand command);
}
