package com.opensocket.aievent.core.dispatch;

/** Outbound port used by execution-control to deliver a command to ai-event-gateway-netty. */
public interface NettyDispatchPort {
    GatewayDispatchResult dispatch(DispatchRequest request);
}
