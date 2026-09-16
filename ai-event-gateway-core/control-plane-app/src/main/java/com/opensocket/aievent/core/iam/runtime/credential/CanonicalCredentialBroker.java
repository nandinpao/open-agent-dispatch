package com.opensocket.aievent.core.iam.runtime.credential;

import com.opensocket.aievent.core.iam.authentication.application.command.AuthenticatePasswordCommand;
import com.opensocket.aievent.core.iam.authentication.application.port.in.PasswordAuthenticationCommandPort;
import com.opensocket.aievent.core.iam.authentication.application.result.PasswordAuthenticationResult;
import com.opensocket.aievent.core.iam.authentication.domain.AuthenticationDomainException;
import com.opensocket.aievent.core.iam.authentication.domain.AuthenticationReasonCode;
import java.util.Objects;
import java.util.Set;

/** Resolves every accepted credential to one canonical IAM subject. */
public final class CanonicalCredentialBroker {
    private final PasswordAuthenticationCommandPort iamPasswords;
    private final LegacyPasswordCredentialAdapter legacyPasswords;

    public CanonicalCredentialBroker(
            PasswordAuthenticationCommandPort iamPasswords,
            LegacyPasswordCredentialAdapter legacyPasswords) {
        this.iamPasswords = Objects.requireNonNull(iamPasswords);
        this.legacyPasswords = Objects.requireNonNull(legacyPasswords);
    }

    public BrokeredAuthentication authenticate(AuthenticatePasswordCommand command) {
        // A linked legacy credential is authoritative only until the canonical IAM
        // password is established. Do not probe the IAM password first: doing so
        // would incorrectly increment IAM failed-attempt counters during migration.
        if (legacyPasswords.hasActiveLink(command.username())) {
            return legacyPasswords.authenticate(command)
                    .map(result -> new BrokeredAuthentication(
                            result, Set.of(LegacyPasswordCredentialAdapter.METHOD)))
                    .orElseThrow(() -> new AuthenticationDomainException(
                            AuthenticationReasonCode.AUTH_INVALID_CREDENTIALS,
                            "Invalid credentials"));
        }
        return new BrokeredAuthentication(iamPasswords.authenticate(command), Set.of("PASSWORD"));
    }

    public record BrokeredAuthentication(
            PasswordAuthenticationResult result,
            Set<String> authenticationMethods) {
        public BrokeredAuthentication {
            authenticationMethods = Set.copyOf(authenticationMethods);
        }
    }
}
