package com.opensocket.aievent.core.iam.persistence.cache;

import com.opensocket.aievent.core.iam.rbac.application.port.out.AuthorizationGrantCachePort;
import java.nio.charset.StandardCharsets;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;

public final class RbacCacheInvalidationMessageListener implements MessageListener {
    private final AuthorizationGrantCachePort cache;

    public RbacCacheInvalidationMessageListener(AuthorizationGrantCachePort cache) {
        this.cache = cache;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        String[] parts = payload.split("\\|", 6);
        if (parts.length < 2) return;
        String tenantId = parts[1];
        if (parts.length < 6 || parts[5].isBlank()) {
            if (tenantId.isBlank()) cache.evictAll();
            else cache.evictTenant(tenantId);
            return;
        }
        for (String principal : parts[5].split(",")) {
            if (!principal.isBlank()) cache.evict(tenantId, principal);
        }
    }
}
