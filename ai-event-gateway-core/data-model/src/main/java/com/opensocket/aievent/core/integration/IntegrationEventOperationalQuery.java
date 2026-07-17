package com.opensocket.aievent.core.integration;
import java.util.Map;
public interface IntegrationEventOperationalQuery { Map<String,Integer> statusCounts(int limit); String storeMode(); }
