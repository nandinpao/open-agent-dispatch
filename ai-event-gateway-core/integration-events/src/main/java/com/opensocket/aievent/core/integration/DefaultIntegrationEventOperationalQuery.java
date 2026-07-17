package com.opensocket.aievent.core.integration;
import java.util.Map;
import org.springframework.stereotype.Service;
@Service
public class DefaultIntegrationEventOperationalQuery implements IntegrationEventOperationalQuery {
    private final IntegrationEventRepository repository;
    public DefaultIntegrationEventOperationalQuery(IntegrationEventRepository repository){this.repository=repository;}
    public Map<String,Integer> statusCounts(int limit){return repository.statusCounts(limit);} public String storeMode(){return repository.mode();}
}
