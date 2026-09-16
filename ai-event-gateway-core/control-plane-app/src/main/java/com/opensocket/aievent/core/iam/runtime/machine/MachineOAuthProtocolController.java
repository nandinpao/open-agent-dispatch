package com.opensocket.aievent.core.iam.runtime.machine;

import com.opensocket.aievent.core.iam.runtime.config.IamMachineTokenProperties;
import com.opensocket.aievent.core.iam.token.application.result.IssuedMachineAccessTokenResult;
import com.opensocket.aievent.core.iam.token.application.service.MachineJwtApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HF06 canonical Machine OAuth protocol endpoints.
 *
 * <p>The Spring Authorization Server filter chain remains enabled and is preferred when the
 * framework materializes its endpoint filters. This MVC controller is an executable fallback for
 * the exact same canonical paths so an otherwise valid deployment can never degrade into a static
 * resource 404/500 merely because an Authorization Server endpoint filter was not registered.
 * Credential validation, Tenant/RBAC authority, machine boundary evaluation, rate limiting and JWT
 * issuance remain owned by {@link IamMachineTokenRuntimeOrchestrator}; this controller does not
 * introduce a second authorization model.</p>
 */
@RestController
@ConditionalOnProperty(prefix = "aeg.iam.machine-token", name = "enabled", havingValue = "true")
public final class MachineOAuthProtocolController {
    private final IamMachineTokenRuntimeOrchestrator orchestrator;
    private final MachineJwtApplicationService jwt;
    private final IamMachineTokenProperties properties;
    private final TrustedClientIpResolver clientIp;
    private final TransactionTemplate transactions;

    public MachineOAuthProtocolController(
            IamMachineTokenRuntimeOrchestrator orchestrator,
            MachineJwtApplicationService jwt,
            IamMachineTokenProperties properties,
            TrustedClientIpResolver clientIp,
            PlatformTransactionManager transactionManager) {
        this.orchestrator = orchestrator;
        this.jwt = jwt;
        this.properties = properties;
        this.clientIp = clientIp;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setName("iam-machine:canonical-protocol");
    }

    @PostMapping(
            path = "/oauth2/token",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> token(
            @RequestParam(name = "grant_type", required = false) String grantType,
            @RequestParam(name = "scope", required = false) String scope,
            @RequestParam(name = "resource", required = false) String resource,
            @RequestParam(name = "audience", required = false) String audience,
            HttpServletRequest request) {
        String correlation = correlation(request);
        ClientSecret client = basic(request.getHeader(HttpHeaders.AUTHORIZATION));
        String requestedResource = requestedResource(resource, audience);
        try {
            IssuedMachineAccessTokenResult issued = orchestrator.exchange(
                    grantType,
                    client.clientId(),
                    client.secret(),
                    scopes(scope),
                    requestedResource,
                    clientIp.resolve(request),
                    correlation);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("access_token", issued.accessToken());
            body.put("token_type", "Bearer");
            body.put("expires_in", issued.expiresInSeconds());
            body.put("scope", String.join(" ", issued.scopes()));
            body.put("resource", issued.audience());
            body.put("audience", issued.audience());
            body.put("correlation_id", correlation);
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .header(HttpHeaders.PRAGMA, "no-cache")
                    .header("X-Correlation-Id", correlation)
                    .body(body);
        } catch (MachineOAuthException e) {
            return error(e, correlation);
        }
    }

    @GetMapping(path = "/oauth2/jwks", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> jwks() {
        String body = transactions.execute(status -> jwt.jwksJson());
        if (body == null || body.isBlank()) throw new IllegalStateException("MACHINE_JWKS_EMPTY");
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(properties.getJwksCacheMaxAgeSeconds())).cachePublic())
                .body(body);
    }

    private String requestedResource(String resource, String audience) {
        String canonical = resource == null ? "" : resource.trim();
        if (!canonical.isBlank()) return canonical;
        if (properties.isAllowLegacyAudienceParameter()) return audience == null ? "" : audience.trim();
        return "";
    }

    private static ResponseEntity<Map<String, Object>> error(MachineOAuthException e, String correlation) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", e.oauthError());
        body.put("error_description", e.getMessage());
        body.put("correlation_id", correlation);
        ResponseEntity.BodyBuilder response = ResponseEntity.status(e.httpStatus())
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header("X-Correlation-Id", correlation);
        if (e.authenticateChallenge()) response.header(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"OpenDispatch OAuth\"");
        if (e.httpStatus() == 429) response.header(HttpHeaders.RETRY_AFTER, "60");
        return response.body(body);
    }

    private static ClientSecret basic(String header) {
        try {
            if (header == null || !header.regionMatches(true, 0, "Basic ", 0, 6)) return new ClientSecret("", "");
            byte[] raw = Base64.getDecoder().decode(header.substring(6).trim());
            if (raw.length > 1024) return new ClientSecret("", "");
            String decoded = new String(raw, StandardCharsets.UTF_8);
            int colon = decoded.indexOf(':');
            if (colon < 1) return new ClientSecret("", "");
            return new ClientSecret(
                    URLDecoder.decode(decoded.substring(0, colon), StandardCharsets.UTF_8),
                    URLDecoder.decode(decoded.substring(colon + 1), StandardCharsets.UTF_8));
        } catch (Exception e) {
            return new ClientSecret("", "");
        }
    }

    private static Set<String> scopes(String scope) {
        if (scope == null || scope.isBlank()) return Set.of();
        TreeSet<String> scopes = new TreeSet<>();
        for (String value : scope.trim().split("\\s+")) if (!value.isBlank()) scopes.add(value);
        return Set.copyOf(scopes);
    }

    private static String correlation(HttpServletRequest request) {
        String value = request.getHeader("X-Correlation-Id");
        return value != null && value.matches("[A-Za-z0-9._:-]{1,128}") ? value : UUID.randomUUID().toString();
    }

    private record ClientSecret(String clientId, String secret) { }
}
