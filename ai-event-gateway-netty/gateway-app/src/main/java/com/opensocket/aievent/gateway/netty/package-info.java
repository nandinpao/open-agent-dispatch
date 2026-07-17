/**
 * AI Event Gateway Netty backend.
 *
 * <p>This package contains the production Netty transport/data-plane runtime: TCP JSON Line,
 * WebSocket Agent/Admin channels, cluster transport visibility, local Agent connection registry,
 * Core directory synchronization, Core callback relay, and command delivery APIs.</p>
 *
 * <p>Production boundary: ai-event-gateway-netty does not own business task assignment, task
 * lifecycle state, dispatch token generation, retry, recovery, deduplication, issue tracking, MCP
 * orchestration, or adapter policy. Those responsibilities belong to ai-event-gateway-core and
 * dedicated adapter services.</p>
 */
package com.opensocket.aievent.gateway.netty;
