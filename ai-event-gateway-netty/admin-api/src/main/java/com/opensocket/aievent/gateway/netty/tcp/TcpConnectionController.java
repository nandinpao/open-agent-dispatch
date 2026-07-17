package com.opensocket.aievent.gateway.netty.tcp;

import com.opensocket.aievent.gateway.netty.tcp.dto.TcpConnectionResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * TCP gateway component for Tcp Connection Controller. It accepts newline-delimited JSON
 * messages, binds connections to agents, and tracks Agent-bound transport connections for Admin UI diagnostics.
 */
@RestController
@RequestMapping("/api/tcp")
public class TcpConnectionController {

    private final TcpConnectionRegistry connectionRegistry;

    public TcpConnectionController(TcpConnectionRegistry connectionRegistry) {
        this.connectionRegistry = connectionRegistry;
    }

    @GetMapping("/connections")
    public TcpConnectionResponse listConnections() {
        return new TcpConnectionResponse(
                connectionRegistry.countActive(),
                connectionRegistry.list()
        );
    }
}
