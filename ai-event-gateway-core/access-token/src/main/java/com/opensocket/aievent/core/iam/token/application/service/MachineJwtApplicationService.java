package com.opensocket.aievent.core.iam.token.application.service;

import com.opensocket.aievent.core.iam.security.contract.MachineAuthenticationContext;
import com.opensocket.aievent.core.iam.token.application.port.out.MachineJwtCodecPort;
import com.opensocket.aievent.core.iam.token.application.port.out.MachineSigningKeyProtectorPort;
import com.opensocket.aievent.core.iam.token.application.port.out.MachineSigningKeyRepository;
import com.opensocket.aievent.core.iam.token.application.result.IssuedMachineAccessTokenResult;
import com.opensocket.aievent.core.iam.token.domain.MachineSigningKey;
import com.opensocket.aievent.core.iam.token.domain.MachineSigningKeyStatus;
import com.opensocket.aievent.core.iam.token.domain.MachineTokenClaims;
import java.security.GeneralSecurityException;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/** Issues and verifies short-lived signed machine JWTs without changing any Human IAM contract. */
public final class MachineJwtApplicationService {
    private final MachineSigningKeyRepository keys;
    private final MachineSigningKeyProtectorPort protector;
    private final MachineJwtCodecPort codec;
    private final Clock clock;
    private final Policy policy;

    public MachineJwtApplicationService(
            MachineSigningKeyRepository keys,
            MachineSigningKeyProtectorPort protector,
            MachineJwtCodecPort codec,
            Clock clock,
            Policy policy) {
        this.keys = keys;
        this.protector = protector;
        this.codec = codec;
        this.clock = clock;
        this.policy = policy.validated();
    }

    public IssuedMachineAccessTokenResult issue(
            MachineAuthenticationContext context,
            String clientId,
            Set<String> requestedScopes,
            String requestedAudience) {
        Instant now = clock.instant();
        Set<String> scopes = requestedScopes == null || requestedScopes.isEmpty()
                ? context.accessBoundary().scopes()
                : normalized(requestedScopes);
        if (scopes.isEmpty() || !context.accessBoundary().scopes().containsAll(scopes)) {
            throw new MachineTokenProtocolException("invalid_scope", "Requested scope exceeds the Service Account machine boundary");
        }
        String audience = selectAudience(context.accessBoundary().audiences(), requestedAudience);
        Instant expiresAt = now.plus(policy.accessTokenTtl());
        if (expiresAt.isAfter(context.expiresAt())) expiresAt = context.expiresAt();
        if (Duration.between(now, expiresAt).compareTo(policy.minimumAccessTokenTtl()) < 0) {
            throw new MachineTokenProtocolException("invalid_client", "Client credential lifetime is too short for a new access token");
        }

        MachineSigningKey key = activeSigningKey(now);
        String privateKey = protector.reveal(key.protectedPrivateKey(), key.protectionKeyId());
        try {
            String jti = UUID.randomUUID().toString();
            var claims = new MachineTokenClaims(
                    policy.issuer(),
                    context.principal().principalId(),
                    context.principal().principalType().name(),
                    context.principal().activeTenant().tenantId(),
                    context.credential().credentialId(),
                    clientId,
                    jti,
                    scopes,
                    Set.of(audience),
                    context.accessBoundary().sourceSystems(),
                    context.accessBoundary().apiPrefixes(),
                    context.accessBoundary().cidrs(),
                    context.securityEpoch(),
                    now,
                    now,
                    expiresAt);
            String compact = codec.encode(new MachineJwtCodecPort.SigningMaterial(
                    key.keyId(), key.algorithm(), key.publicKeyDerBase64(), privateKey), claims);
            return new IssuedMachineAccessTokenResult(
                    compact, "Bearer", Math.max(1, Duration.between(now, expiresAt).toSeconds()),
                    scopes, audience, jti, key.keyId(), now, expiresAt);
        } finally {
            privateKey = null;
        }
    }

    public MachineJwtCodecPort.DecodedToken verify(String compactToken) {
        Instant now = clock.instant();
        return codec.decodeAndVerify(compactToken, keys.publishable(now), policy.issuer(), policy.clockSkew(), now);
    }

    /** Cryptographic/time/issuer verification. Resource servers then resolve current IAM epoch for the signed principal. */
    public MachineJwtCodecPort.DecodedToken verify(String compactToken, com.opensocket.aievent.core.iam.security.contract.SecurityEpoch currentEpoch) {
        MachineJwtCodecPort.DecodedToken decoded = verify(compactToken);
        if (!decoded.claims().hasCurrentSecurityEpoch(currentEpoch)) {
            throw new MachineTokenProtocolException("invalid_token", "Machine access token security epoch is stale");
        }
        return decoded;
    }

    /** Ensures a publishable active key exists before discovery/JWKS is served. */
    public void ensureSigningKey() {
        activeSigningKey(clock.instant());
    }

    public String jwksJson() {
        ensureSigningKey();
        return codec.jwksJson(keys.publishable(clock.instant()));
    }

    private MachineSigningKey activeSigningKey(Instant now) {
        var active = keys.active();
        if (active.isPresent() && !active.get().needsRotation(now)) return active.get();
        MachineSigningKey candidate = generate(now);
        return keys.activateIfRequired(candidate, now, now.plus(policy.verificationGrace()));
    }

    private MachineSigningKey generate(Instant now) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(policy.rsaKeySize());
            var pair = generator.generateKeyPair();
            var pub = (RSAPublicKey) pair.getPublic();
            var priv = (RSAPrivateKey) pair.getPrivate();
            String publicDer = Base64.getEncoder().encodeToString(pub.getEncoded());
            String privateDer = Base64.getEncoder().encodeToString(priv.getEncoded());
            var protectedKey = protector.protect(privateDer);
            privateDer = null;
            String kid = "odp-mk-" + UUID.randomUUID();
            return new MachineSigningKey(
                    kid, "RS256", publicDer, protectedKey.protectedValue(), protectedKey.protectionKeyId(),
                    MachineSigningKeyStatus.ACTIVE, now, now.plus(policy.rotationInterval()), null,
                    now, now, 1);
        } catch (GeneralSecurityException e) {
            throw new MachineTokenProtocolException("server_error", "Machine JWT signing key generation failed", e);
        }
    }

    private String selectAudience(Set<String> allowed, String requested) {
        if (requested != null && !requested.isBlank()) {
            String audience = requested.trim();
            if (!allowed.contains(audience)) throw new MachineTokenProtocolException("invalid_target", "Requested audience is not allowed");
            return audience;
        }
        if (allowed.size() != 1) {
            throw new MachineTokenProtocolException("invalid_target", "An explicit audience is required for this Service Account");
        }
        return allowed.iterator().next();
    }

    private static Set<String> normalized(Set<String> values) {
        TreeSet<String> out = new TreeSet<>();
        for (String value : values) if (value != null && !value.isBlank()) out.add(value.trim());
        return Set.copyOf(out);
    }

    public record Policy(
            String issuer,
            Duration accessTokenTtl,
            Duration minimumAccessTokenTtl,
            Duration clockSkew,
            Duration rotationInterval,
            Duration verificationGrace,
            int rsaKeySize) {
        Policy validated() {
            if (issuer == null || issuer.isBlank()) throw new IllegalArgumentException("machine token issuer is required");
            positive(accessTokenTtl, "accessTokenTtl");
            positive(minimumAccessTokenTtl, "minimumAccessTokenTtl");
            if (minimumAccessTokenTtl.compareTo(accessTokenTtl) > 0) throw new IllegalArgumentException("minimumAccessTokenTtl exceeds accessTokenTtl");
            if (clockSkew == null || clockSkew.isNegative()) throw new IllegalArgumentException("clockSkew must not be negative");
            positive(rotationInterval, "rotationInterval");
            positive(verificationGrace, "verificationGrace");
            if (verificationGrace.compareTo(accessTokenTtl.plus(clockSkew)) < 0) {
                throw new IllegalArgumentException("verificationGrace must cover accessTokenTtl plus clockSkew");
            }
            if (rsaKeySize < 2048) throw new IllegalArgumentException("rsaKeySize must be at least 2048");
            return this;
        }
        private static void positive(Duration value, String name) {
            if (value == null || value.isZero() || value.isNegative()) throw new IllegalArgumentException(name + " must be positive");
        }
    }

    public static final class MachineTokenProtocolException extends RuntimeException {
        private final String oauthError;
        public MachineTokenProtocolException(String oauthError, String message) { super(message); this.oauthError = oauthError; }
        public MachineTokenProtocolException(String oauthError, String message, Throwable cause) { super(message, cause); this.oauthError = oauthError; }
        public String oauthError() { return oauthError; }
    }
}
