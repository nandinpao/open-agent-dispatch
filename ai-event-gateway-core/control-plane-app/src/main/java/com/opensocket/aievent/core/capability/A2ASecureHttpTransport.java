package com.opensocket.aievent.core.capability;

import com.opensocket.aievent.core.security.outbound.OutboundDestinationPolicy;
import com.opensocket.aievent.core.security.outbound.OutboundDestinationValidator;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import org.springframework.stereotype.Component;

/**
 * PC-S4 dedicated External A2A HTTP/1.1 transport.
 *
 * <p>The destination is DNS-resolved exactly once by {@link OutboundDestinationValidator}; the
 * socket connects to one of those validated InetAddress objects. HTTPS then uses the original
 * hostname for SNI and endpoint identification. This closes the validate-hostname/re-resolve gap
 * present when a hostname URI is handed back to the JDK HttpClient.</p>
 */
@Component
public final class A2ASecureHttpTransport {
    private static final int MAX_HEADER_BYTES = 65_536;
    private static final int MAX_HEADER_LINE_BYTES = 8_192;
    private final OutboundDestinationValidator destinations;

    public A2ASecureHttpTransport(OutboundDestinationValidator destinations) { this.destinations = destinations; }

    public ExchangeResponse exchange(
            String method,
            String url,
            Map<String, String> headers,
            byte[] body,
            A2AExternalF0SecurityService.RuntimeSecurity security) {
        OutboundDestinationPolicy policy = requireSecurity(security);
        byte[] requestBody = body == null ? new byte[0] : body;
        if (requestBody.length > policy.maxRequestBytes()) throw new IllegalArgumentException("A2A_REQUEST_TOO_LARGE");
        try (Connection c = connect(url, security)) {
            writeRequest(c, method, headers, requestBody);
            ResponseHead head = readHead(c.in());
            byte[] responseBody = readBody(c.in(), head.headers(), policy.maxResponseBytes());
            return new ExchangeResponse(head.statusCode(), head.headers(), responseBody);
        } catch (SocketTimeoutException ex) {
            throw new IllegalStateException("A2A_HTTP_IDLE_TIMEOUT", ex);
        } catch (Exception ex) {
            if (ex instanceof IllegalArgumentException iae) throw iae;
            if (ex instanceof IllegalStateException ise) throw ise;
            throw new IllegalStateException("A2A_SECURE_TRANSPORT_FAILED:" + safe(ex.getMessage()), ex);
        }
    }

    public SseResponse subscribe(
            String url,
            Map<String, String> headers,
            byte[] body,
            A2AExternalF0SecurityService.RuntimeSecurity security) {
        OutboundDestinationPolicy policy = requireSecurity(security);
        byte[] requestBody = body == null ? new byte[0] : body;
        if (requestBody.length > policy.maxRequestBytes()) throw new IllegalArgumentException("A2A_REQUEST_TOO_LARGE");
        long deadline = System.nanoTime() + Duration.ofMillis(policy.sseOverallTimeoutMs()).toNanos();
        try (Connection c = connect(url, security)) {
            writeRequest(c, "POST", headers, requestBody);
            ResponseHead head = readHead(c.in());
            if (head.statusCode() < 200 || head.statusCode() >= 300) {
                byte[] responseBody = readBody(c.in(), head.headers(), policy.maxResponseBytes());
                return new SseResponse(head.statusCode(), head.headers(), List.of(), responseBody);
            }
            InputStream bodyIn = responseBodyStream(c.in(), head.headers());
            List<String> events = new ArrayList<>();
            StringBuilder data = new StringBuilder();
            int streamBytes = 0;
            while (true) {
                long remainingMs = Math.max(1L, Duration.ofNanos(deadline - System.nanoTime()).toMillis());
                if (deadline - System.nanoTime() <= 0) throw new IllegalStateException("A2A_SSE_OVERALL_TIMEOUT");
                c.socket().setSoTimeout((int) Math.min(policy.sseIdleTimeoutMs(), remainingMs));
                String line;
                try { line = readLine(bodyIn, Math.min(policy.maxSseEventBytes(), MAX_HEADER_LINE_BYTES * 8)); }
                catch (SocketTimeoutException ex) { throw new IllegalStateException("A2A_SSE_IDLE_TIMEOUT", ex); }
                if (line == null) {
                    flushEvent(data, events, policy);
                    break;
                }
                streamBytes += line.getBytes(StandardCharsets.UTF_8).length + 1;
                if (streamBytes > policy.maxSseStreamBytes()) throw new IllegalStateException("A2A_SSE_STREAM_TOO_LARGE");
                if (line.isEmpty()) {
                    flushEvent(data, events, policy);
                    continue;
                }
                if (line.startsWith(":")) continue;
                if (line.startsWith("data:")) {
                    String value = line.substring(5);
                    if (value.startsWith(" ")) value = value.substring(1);
                    if (!data.isEmpty()) data.append('\n');
                    data.append(value);
                    if (data.toString().getBytes(StandardCharsets.UTF_8).length > policy.maxSseEventBytes()) {
                        throw new IllegalStateException("A2A_SSE_EVENT_TOO_LARGE");
                    }
                }
            }
            return new SseResponse(head.statusCode(), head.headers(), List.copyOf(events), new byte[0]);
        } catch (Exception ex) {
            if (ex instanceof IllegalArgumentException iae) throw iae;
            if (ex instanceof IllegalStateException ise) throw ise;
            throw new IllegalStateException("A2A_SUBSCRIBE_FAILED:" + safe(ex.getMessage()), ex);
        }
    }

    private Connection connect(String rawUrl, A2AExternalF0SecurityService.RuntimeSecurity security) throws Exception {
        OutboundDestinationPolicy policy = requireSecurity(security);
        if (!policy.dnsRebindingProtection()) throw new IllegalArgumentException("A2A_DNS_REBINDING_PROTECTION_REQUIRED");
        if (policy.maxRedirects() != 0) throw new IllegalArgumentException("A2A_REDIRECTS_NOT_RUNTIME_SUPPORTED");
        OutboundDestinationValidator.ResolvedDestination destination = destinations.resolveAllowed(rawUrl, policy, "A2A_RUNTIME");
        URI uri = destination.uri();
        int port = uri.getPort() > 0 ? uri.getPort() : ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80);
        Exception last = null;
        for (InetAddress address : destination.addresses()) {
            Socket raw = new Socket();
            try {
                raw.connect(new InetSocketAddress(address, port), security.connectTimeoutMs());
                raw.setSoTimeout(security.readTimeoutMs());
                if ("https".equalsIgnoreCase(uri.getScheme())) {
                    SSLSocket ssl = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault())
                            .createSocket(raw, destination.host(), port, true);
                    SSLParameters parameters = ssl.getSSLParameters();
                    parameters.setEndpointIdentificationAlgorithm("HTTPS");
                    parameters.setServerNames(List.of(new SNIHostName(destination.host())));
                    String tls = normalizedTls(policy.requiredTlsVersion());
                    if (tls != null) parameters.setProtocols(new String[]{tls});
                    ssl.setSSLParameters(parameters);
                    ssl.setSoTimeout(security.readTimeoutMs());
                    ssl.startHandshake();
                    enforcePins(ssl, policy.certificatePins());
                    return new Connection(uri, destination.host(), ssl, new BufferedInputStream(ssl.getInputStream()), ssl.getOutputStream());
                }
                if (policy.requiredTlsVersion() != null || !policy.certificatePins().isEmpty()) {
                    raw.close();
                    throw new IllegalArgumentException("A2A_TLS_POLICY_REQUIRES_HTTPS");
                }
                return new Connection(uri, destination.host(), raw, new BufferedInputStream(raw.getInputStream()), raw.getOutputStream());
            } catch (Exception ex) {
                last = ex;
                try { raw.close(); } catch (Exception ignored) {}
            }
        }
        if (last instanceof IllegalArgumentException iae) throw iae;
        throw new IOException("No validated destination address could be connected", last);
    }

    private static void enforcePins(SSLSocket ssl, List<String> pins) throws Exception {
        if (pins == null || pins.isEmpty()) return;
        Certificate[] peer = ssl.getSession().getPeerCertificates();
        if (peer.length == 0 || !(peer[0] instanceof X509Certificate leaf)) throw new IllegalStateException("A2A_TLS_PEER_CERTIFICATE_REQUIRED");
        byte[] spki = leaf.getPublicKey().getEncoded();
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(spki);
        String b64 = "sha256/" + Base64.getEncoder().encodeToString(digest);
        String hex = "sha256:" + HexFormat.of().formatHex(digest);
        boolean matched = pins.stream().anyMatch(pin -> pin.equals(b64) || pin.equalsIgnoreCase(hex));
        if (!matched) throw new IllegalStateException("A2A_TLS_CERTIFICATE_PIN_MISMATCH");
    }

    private static String normalizedTls(String value) {
        if (value == null || value.isBlank()) return null;
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "TLSV1.2", "TLS1.2", "1.2" -> "TLSv1.2";
            case "TLSV1.3", "TLS1.3", "1.3" -> "TLSv1.3";
            default -> throw new IllegalArgumentException("A2A_TLS_VERSION_NOT_SUPPORTED");
        };
    }

    private static OutboundDestinationPolicy requireSecurity(A2AExternalF0SecurityService.RuntimeSecurity security) {
        if (security == null) throw new IllegalArgumentException("A2A_EXECUTION_SECURITY_SNAPSHOT_REQUIRED");
        return security.outboundPolicy();
    }

    private static void writeRequest(Connection c, String method, Map<String, String> headers, byte[] body) throws IOException {
        URI uri = c.uri();
        String path = uri.getRawPath();
        if (path == null || path.isBlank()) path = "/";
        if (uri.getRawQuery() != null) path += "?" + uri.getRawQuery();
        StringBuilder request = new StringBuilder();
        request.append(method).append(' ').append(path).append(" HTTP/1.1\r\n");
        request.append("Host: ").append(hostHeader(uri, c.host())).append("\r\n");
        request.append("Connection: close\r\n");
        request.append("Content-Length: ").append(body.length).append("\r\n");
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String name = safeHeader(entry.getKey(), true);
            String value = safeHeader(entry.getValue(), false);
            request.append(name).append(": ").append(value).append("\r\n");
        }
        request.append("\r\n");
        c.out().write(request.toString().getBytes(StandardCharsets.ISO_8859_1));
        if (body.length > 0) c.out().write(body);
        c.out().flush();
    }

    private static String safeHeader(String value, boolean name) {
        if (value == null || value.isBlank() || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new IllegalArgumentException(name ? "A2A_HTTP_HEADER_NAME_INVALID" : "A2A_HTTP_HEADER_VALUE_INVALID");
        }
        if (name && !value.matches("[A-Za-z0-9!#$%&'*+.^_`|~-]+")) throw new IllegalArgumentException("A2A_HTTP_HEADER_NAME_INVALID");
        return value;
    }

    private static String hostHeader(URI uri, String host) {
        String h = host.contains(":") ? "[" + host + "]" : host;
        int port = uri.getPort();
        boolean defaultPort = port < 0 || ("https".equalsIgnoreCase(uri.getScheme()) && port == 443) || ("http".equalsIgnoreCase(uri.getScheme()) && port == 80);
        return defaultPort ? h : h + ":" + port;
    }

    private static ResponseHead readHead(InputStream in) throws IOException {
        int headerBytes = 0;
        String status = readLine(in, MAX_HEADER_LINE_BYTES);
        if (status == null || !status.startsWith("HTTP/")) throw new IOException("A2A_HTTP_STATUS_LINE_INVALID");
        headerBytes += status.length() + 2;
        String[] parts = status.split(" ", 3);
        if (parts.length < 2) throw new IOException("A2A_HTTP_STATUS_LINE_INVALID");
        int statusCode = Integer.parseInt(parts[1]);
        Map<String, String> headers = new LinkedHashMap<>();
        while (true) {
            String line = readLine(in, MAX_HEADER_LINE_BYTES);
            if (line == null) throw new EOFException("A2A_HTTP_HEADERS_TRUNCATED");
            headerBytes += line.length() + 2;
            if (headerBytes > MAX_HEADER_BYTES) throw new IOException("A2A_HTTP_HEADERS_TOO_LARGE");
            if (line.isEmpty()) break;
            int colon = line.indexOf(':');
            if (colon <= 0) throw new IOException("A2A_HTTP_HEADER_INVALID");
            String name = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();
            headers.merge(name, value, (a, b) -> a + "," + b);
        }
        return new ResponseHead(statusCode, Map.copyOf(headers));
    }

    private static byte[] readBody(InputStream in, Map<String, String> headers, int maxBytes) throws IOException {
        String transfer = headers.getOrDefault("transfer-encoding", "").toLowerCase(Locale.ROOT);
        String lengthValue = headers.get("content-length");
        if (!transfer.contains("chunked") && lengthValue != null && !lengthValue.isBlank()) {
            long length = Long.parseLong(lengthValue.trim());
            if (length > maxBytes) throw new IOException("A2A_RESPONSE_TOO_LARGE");
            if (length < 0 || length > Integer.MAX_VALUE) throw new IOException("A2A_HTTP_CONTENT_LENGTH_INVALID");
            byte[] result = new byte[(int) length];
            int offset = 0;
            while (offset < result.length) {
                int n = in.read(result, offset, result.length - offset);
                if (n < 0) throw new EOFException("A2A_HTTP_BODY_TRUNCATED");
                offset += n;
            }
            return result;
        }
        InputStream body = responseBodyStream(in, headers);
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(maxBytes, 16_384));
        byte[] buffer = new byte[8_192];
        int total = 0;
        for (int n; (n = body.read(buffer)) >= 0;) {
            total += n;
            if (total > maxBytes) throw new IOException("A2A_RESPONSE_TOO_LARGE");
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    private static InputStream responseBodyStream(InputStream in, Map<String, String> headers) {
        String transfer = headers.getOrDefault("transfer-encoding", "").toLowerCase(Locale.ROOT);
        return transfer.contains("chunked") ? new ChunkedInputStream(in) : in;
    }

    private static void flushEvent(StringBuilder data, List<String> events, OutboundDestinationPolicy policy) {
        if (data.isEmpty()) return;
        if (events.size() >= policy.maxSseEvents()) throw new IllegalStateException("A2A_SSE_EVENT_LIMIT_REACHED");
        if (data.toString().getBytes(StandardCharsets.UTF_8).length > policy.maxSseEventBytes()) throw new IllegalStateException("A2A_SSE_EVENT_TOO_LARGE");
        events.add(data.toString());
        data.setLength(0);
    }

    private static String readLine(InputStream in, int maxBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        while (true) {
            int b = in.read();
            if (b < 0) return out.size() == 0 ? null : out.toString(StandardCharsets.ISO_8859_1);
            if (b == '\n') {
                byte[] bytes = out.toByteArray();
                int len = bytes.length;
                if (len > 0 && bytes[len - 1] == '\r') len--;
                return new String(bytes, 0, len, StandardCharsets.ISO_8859_1);
            }
            out.write(b);
            if (out.size() > maxBytes) throw new IOException("A2A_HTTP_LINE_TOO_LARGE");
        }
    }

    private static String safe(String value) { return value == null ? "" : value.replace('\n', ' ').replace('\r', ' '); }

    public record ExchangeResponse(int statusCode, Map<String, String> headers, byte[] body) {}
    public record SseResponse(int statusCode, Map<String, String> headers, List<String> events, byte[] errorBody) {}
    private record ResponseHead(int statusCode, Map<String, String> headers) {}

    private record Connection(URI uri, String host, Socket socket, InputStream in, OutputStream out) implements AutoCloseable {
        @Override public void close() throws IOException { socket.close(); }
    }

    private static final class ChunkedInputStream extends InputStream {
        private final InputStream in;
        private long remaining;
        private boolean done;
        private boolean needChunkHeader = true;
        ChunkedInputStream(InputStream in) { this.in = in; }
        @Override public int read() throws IOException {
            byte[] one = new byte[1];
            int n = read(one, 0, 1);
            return n < 0 ? -1 : one[0] & 0xff;
        }
        @Override public int read(byte[] b, int off, int len) throws IOException {
            if (done) return -1;
            if (needChunkHeader) readChunkHeader();
            if (done) return -1;
            int allowed = (int) Math.min(len, remaining);
            int n = in.read(b, off, allowed);
            if (n < 0) throw new EOFException("A2A_HTTP_CHUNK_TRUNCATED");
            remaining -= n;
            if (remaining == 0) {
                consumeCrlf();
                needChunkHeader = true;
            }
            return n;
        }
        private void readChunkHeader() throws IOException {
            String line = A2ASecureHttpTransport.readLine(in, 128);
            if (line == null) throw new EOFException("A2A_HTTP_CHUNK_HEADER_TRUNCATED");
            int semi = line.indexOf(';');
            String size = (semi >= 0 ? line.substring(0, semi) : line).trim();
            try { remaining = Long.parseLong(size, 16); }
            catch (NumberFormatException ex) { throw new IOException("A2A_HTTP_CHUNK_SIZE_INVALID", ex); }
            needChunkHeader = false;
            if (remaining == 0) {
                while (true) {
                    String trailer = A2ASecureHttpTransport.readLine(in, MAX_HEADER_LINE_BYTES);
                    if (trailer == null || trailer.isEmpty()) break;
                }
                done = true;
            }
        }
        private void consumeCrlf() throws IOException {
            int cr = in.read(), lf = in.read();
            if (cr != '\r' || lf != '\n') throw new IOException("A2A_HTTP_CHUNK_DELIMITER_INVALID");
        }
    }
}
