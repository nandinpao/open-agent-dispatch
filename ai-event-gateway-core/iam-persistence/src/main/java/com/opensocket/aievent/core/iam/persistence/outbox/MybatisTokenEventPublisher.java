package com.opensocket.aievent.core.iam.persistence.outbox;

import com.opensocket.aievent.core.iam.token.application.port.out.TokenEventPublisher;
import com.opensocket.aievent.core.iam.token.event.TokenDomainEvent;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;
import java.util.Map;
import java.util.TreeMap;

@DatabaseRepositoryAdapter
public class MybatisTokenEventPublisher implements TokenEventPublisher {
    private final IamTransactionalOutboxWriter outbox;

    public MybatisTokenEventPublisher(IamTransactionalOutboxWriter outbox) {
        this.outbox = outbox;
    }

    @Override
    public void publish(TokenDomainEvent event) {
        boolean serviceAccountEvent = event.tokenId().isBlank();
        outbox.append(
                event.eventId(),
                event.eventType(),
                serviceAccountEvent ? "SERVICE_ACCOUNT" : "ACCESS_TOKEN",
                serviceAccountEvent ? event.principalId() : event.tokenId(),
                json(event),
                event.occurredAt(),
                event.tenantId(),
                event.correlationId(),
                event.actorId());
    }

    private static String json(TokenDomainEvent event) {
        StringBuilder json = new StringBuilder(384)
                .append('{')
                .append(field("eventId", event.eventId())).append(',')
                .append(field("eventType", event.eventType())).append(',')
                .append(field("tenantId", event.tenantId())).append(',')
                .append(field("principalType", event.principalType())).append(',')
                .append(field("principalId", event.principalId())).append(',')
                .append(field("tokenId", event.tokenId())).append(',')
                .append(field("actorId", event.actorId())).append(',')
                .append(field("correlationId", event.correlationId())).append(',')
                .append(field("reasonCode", event.reasonCode())).append(',')
                .append(field("occurredAt", event.occurredAt().toString())).append(',')
                .append("\"metadata\":{");
        boolean first = true;
        for (Map.Entry<String, String> entry : new TreeMap<>(event.metadata()).entrySet()) {
            if (sensitive(entry.getKey())) continue;
            if (!first) json.append(',');
            json.append(field(entry.getKey(), entry.getValue()));
            first = false;
        }
        return json.append("}}").toString();
    }

    private static boolean sensitive(String key) {
        if (key == null) return true;
        String normalized = key.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("password")
                || normalized.contains("secret")
                || normalized.contains("cookie")
                || normalized.equals("fulltoken")
                || normalized.equals("rawtoken")
                || normalized.equals("presentedtoken");
    }

    private static String field(String key, String value) {
        return "\"" + escape(key) + "\":\"" + escape(value) + "\"";
    }

    private static String escape(String value) {
        return value == null ? "" : value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
