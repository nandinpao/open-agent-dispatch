package com.opensocket.aievent.core.uicapability.core;

import java.util.Optional;

public interface UiPageCatalogResolver { Optional<UiPageDefinition> resolve(String routeContext); }
