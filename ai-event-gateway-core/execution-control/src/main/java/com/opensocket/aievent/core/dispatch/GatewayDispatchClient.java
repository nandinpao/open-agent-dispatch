package com.opensocket.aievent.core.dispatch;

/**
 * Backward-compatible alias retained for integrations that referenced the pre-M4 name.
 * New execution-control code depends on {@link NettyDispatchPort}.
 */
@Deprecated(forRemoval = false)
public interface GatewayDispatchClient extends NettyDispatchPort {
}
