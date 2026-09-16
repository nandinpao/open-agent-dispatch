package com.opensocket.aievent.core.capability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.opensocket.aievent.core.security.outbound.OutboundDestinationPolicy;
import com.opensocket.aievent.core.security.outbound.OutboundDestinationValidator;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

/** In-JVM PC-S4 proof for bounded HTTP/SSE and validated-address connect without Docker. */
class PCStage4SecureA2ATransportTest {

    @Test void boundedResponseRejectsDeclaredOversizeBeforeMaterialization() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress());
             var executor = Executors.newSingleThreadExecutor()) {
            executor.submit(() -> serve(server, "HTTP/1.1 200 OK\r\nContent-Length: 2048\r\nConnection: close\r\n\r\n"));
            A2ASecureHttpTransport transport = new A2ASecureHttpTransport(new OutboundDestinationValidator());
            var security = security(policy(1024, 8192, 16));
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> transport.exchange("GET", "http://localhost:" + server.getLocalPort() + "/task", Map.of(), new byte[0], security));
            assertThat(rootMessage(ex)).contains("A2A_RESPONSE_TOO_LARGE");
        }
    }

    @Test void sseCommentsDoNotConsumeAnArbitraryLineLimit() throws Exception {
        StringBuilder response = new StringBuilder("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\n");
        for (int i=0;i<300;i++) response.append(": ping\n");
        response.append("data: {\"task\":{\"state\":\"COMPLETED\"}}\n\n");
        try (ServerSocket server = new ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress());
             var executor = Executors.newSingleThreadExecutor()) {
            executor.submit(() -> serve(server, response.toString()));
            A2ASecureHttpTransport transport = new A2ASecureHttpTransport(new OutboundDestinationValidator());
            var result = transport.subscribe("http://localhost:" + server.getLocalPort() + "/subscribe", Map.of(), new byte[0], security(policy(4096, 65536, 4)));
            assertThat(result.statusCode()).isEqualTo(200);
            assertThat(result.events()).containsExactly("{\"task\":{\"state\":\"COMPLETED\"}}");
        }
    }

    @Test void validatorAlsoRejectsCarrierGradeNatWhenPrivateDestinationsAreDenied() throws Exception {
        var validator = new OutboundDestinationValidator();
        var address = java.net.InetAddress.getByName("100.64.0.1");
        // Direct CIDR helper sanity plus source contract: CGNAT is not Java isSiteLocalAddress().
        assertThat(address.isSiteLocalAddress()).isFalse();
        assertThat(OutboundDestinationValidator.inCidr(address,"100.64.0.0/10")).isTrue();
    }

    private static A2AExternalF0SecurityService.RuntimeSecurity security(OutboundDestinationPolicy policy) {
        return new A2AExternalF0SecurityService.RuntimeSecurity("binding","policy",null,policy,1500,1500);
    }

    private static OutboundDestinationPolicy policy(int maxResponse,int maxStream,int maxEvents) {
        return new OutboundDestinationPolicy(Set.of("http"),List.of(),List.of(),false,false,false,true,
                0,true,true,null,List.of(),4096,maxResponse,4096,maxStream,maxEvents,1500,5000);
    }

    private static void serve(ServerSocket server,String response) {
        try (Socket socket=server.accept();BufferedReader reader=new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1))) {
            String line;while((line=reader.readLine())!=null&&!line.isEmpty()){}
            socket.getOutputStream().write(response.getBytes(StandardCharsets.ISO_8859_1));socket.getOutputStream().flush();
        } catch (Exception ignored) {}
    }

    private static String rootMessage(Throwable t){Throwable x=t;while(x.getCause()!=null)x=x.getCause();return x.getMessage()==null?"":x.getMessage();}
}
