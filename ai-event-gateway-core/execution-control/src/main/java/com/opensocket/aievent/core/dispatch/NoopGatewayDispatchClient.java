package com.opensocket.aievent.core.dispatch;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "dispatch.client", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoopGatewayDispatchClient implements GatewayDispatchClient {
    @Override
    public GatewayDispatchResult dispatch(DispatchRequest request) {
        return GatewayDispatchResult.failure(0, "DISPATCH_CLIENT_DISABLED", "dispatch.client.enabled=false; core did not call ai-event-gateway-netty");
    }
}
