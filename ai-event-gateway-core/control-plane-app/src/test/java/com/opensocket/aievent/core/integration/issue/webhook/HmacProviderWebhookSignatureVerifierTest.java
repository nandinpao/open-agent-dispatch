package com.opensocket.aievent.core.integration.issue.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import com.opensocket.aievent.core.integration.identity.IntegrationAuthType;
import com.opensocket.aievent.core.integration.identity.IntegrationCredentialMetadata;
import com.opensocket.aievent.core.integration.identity.IntegrationCredentialStatus;
import com.opensocket.aievent.core.integration.identity.IntegrationSecretResolver;
import com.opensocket.aievent.core.integration.identity.ResolvedIntegrationSecret;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

class HmacProviderWebhookSignatureVerifierTest {

    private static final String SECRET = "phase3f-r1-test-secret";

    @Test
    void bindsEverySecurityDimensionAndAcceptsActiveOrGraceCredential() throws Exception {
        var verifier = verifier();
        var active = credential("active", IntegrationCredentialStatus.ACTIVE);
        var grace = credential("grace", IntegrationCredentialStatus.GRACE_PERIOD);
        var request = request("body-a");
        String signature = hmac(request.signingInput());

        assertThat(verifier.verify(request, "sha256=" + signature, List.of(active)).verified()).isTrue();
        assertThat(verifier.verify(request, "v1=" + signature, List.of(grace)).verified()).isTrue();

        for (var changed : List.of(
                new ProviderWebhookSigningRequest(
                        "PUT",
                        request.canonicalPath(),
                        request.connectionId(),
                        request.providerType(),
                        request.providerEventId(),
                        request.timestamp(),
                        request.nonce(),
                        request.bodyDigest()),
                new ProviderWebhookSigningRequest(
                        request.method(),
                        "/changed",
                        request.connectionId(),
                        request.providerType(),
                        request.providerEventId(),
                        request.timestamp(),
                        request.nonce(),
                        request.bodyDigest()),
                new ProviderWebhookSigningRequest(
                        request.method(),
                        request.canonicalPath(),
                        "other",
                        request.providerType(),
                        request.providerEventId(),
                        request.timestamp(),
                        request.nonce(),
                        request.bodyDigest()),
                new ProviderWebhookSigningRequest(
                        request.method(),
                        request.canonicalPath(),
                        request.connectionId(),
                        "REDMINE",
                        request.providerEventId(),
                        request.timestamp(),
                        request.nonce(),
                        request.bodyDigest()),
                new ProviderWebhookSigningRequest(
                        request.method(),
                        request.canonicalPath(),
                        request.connectionId(),
                        request.providerType(),
                        "evt-other",
                        request.timestamp(),
                        request.nonce(),
                        request.bodyDigest()),
                new ProviderWebhookSigningRequest(
                        request.method(),
                        request.canonicalPath(),
                        request.connectionId(),
                        request.providerType(),
                        request.providerEventId(),
                        "2000-01-01T00:00:00Z",
                        request.nonce(),
                        request.bodyDigest()),
                new ProviderWebhookSigningRequest(
                        request.method(),
                        request.canonicalPath(),
                        request.connectionId(),
                        request.providerType(),
                        request.providerEventId(),
                        request.timestamp(),
                        "nonce-other",
                        request.bodyDigest()),
                request("body-modified"))) {
            assertThat(verifier.verify(changed, signature, List.of(active)).verified()).isFalse();
        }
    }

    @Test
    void revokedExpiredAndMissingResolverFailClosed() throws Exception {
        var request = request("body-a");
        var revoked = credential("revoked", IntegrationCredentialStatus.REVOKED);
        var expired = new IntegrationCredentialMetadata(
                "tenant-a",
                "expired",
                "principal-a",
                IntegrationAuthType.WEBHOOK_HMAC,
                "env://WEBHOOK_TEST",
                "v0",
                "0000",
                OffsetDateTime.now().minusDays(2),
                OffsetDateTime.now().minusDays(1),
                null,
                null,
                IntegrationCredentialStatus.ACTIVE,
                1,
                OffsetDateTime.now(),
                OffsetDateTime.now());

        assertThat(verifier().verify(
                        request,
                        hmac(request.signingInput()),
                        List.of(revoked, expired))
                .verified())
                .isFalse();

        var emptyFactory = new StaticListableBeanFactory();
        var verifierWithoutResolver = new HmacProviderWebhookSignatureVerifier(
                emptyFactory.getBeanProvider(IntegrationSecretResolver.class));

        assertThat(verifierWithoutResolver.verify(
                        request,
                        hmac(request.signingInput()),
                        List.of(credential("active", IntegrationCredentialStatus.ACTIVE)))
                .reasonCode())
                .isEqualTo("WEBHOOK_SECRET_RESOLVER_UNAVAILABLE");
    }

    private HmacProviderWebhookSignatureVerifier verifier() {
        var factory = new StaticListableBeanFactory();
        factory.addBean("resolver", new IntegrationSecretResolver() {
            @Override
            public ResolvedIntegrationSecret resolve(IntegrationCredentialMetadata credential) {
                return new ResolvedIntegrationSecret(SECRET.toCharArray());
            }

            @Override
            public String mode() {
                return "TEST";
            }

            @Override
            public boolean supports(IntegrationCredentialMetadata credential) {
                return true;
            }
        });
        return new HmacProviderWebhookSignatureVerifier(
                factory.getBeanProvider(IntegrationSecretResolver.class));
    }

    private IntegrationCredentialMetadata credential(String id, IntegrationCredentialStatus status) {
        var now = OffsetDateTime.now();
        return new IntegrationCredentialMetadata(
                "tenant-a",
                id,
                "principal-a",
                IntegrationAuthType.WEBHOOK_HMAC,
                "env://WEBHOOK_TEST",
                "v1",
                "1234",
                now.minusMinutes(1),
                now.plusDays(1),
                null,
                null,
                status,
                1,
                now,
                now);
    }

    private ProviderWebhookSigningRequest request(String body) {
        return new ProviderWebhookSigningRequest(
                "POST",
                "/api/external/provider-webhooks/token",
                "conn-a",
                "JIRA",
                "evt-a",
                "2026-07-27T00:00:00Z",
                "nonce-a",
                sha(body));
    }

    private String hmac(String input) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
    }

    private String sha(String input) {
        try {
            return HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256")
                            .digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to compute test SHA-256 digest.", exception);
        }
    }
}
