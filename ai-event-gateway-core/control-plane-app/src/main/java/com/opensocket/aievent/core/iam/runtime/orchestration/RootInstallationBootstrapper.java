package com.opensocket.aievent.core.iam.runtime.orchestration;

import com.opensocket.aievent.core.iam.authentication.application.command.RootBootstrapStepCommand;
import com.opensocket.aievent.core.iam.authentication.application.command.SetPasswordCommand;
import com.opensocket.aievent.core.iam.authentication.application.port.in.PasswordAuthenticationCommandPort;
import com.opensocket.aievent.core.iam.authentication.application.port.in.RootAuthenticationCommandPort;
import com.opensocket.aievent.core.iam.authentication.application.port.out.AuthenticationEventPublisher;
import com.opensocket.aievent.core.iam.authentication.application.port.out.RootBootstrapStateRepository;
import com.opensocket.aievent.core.iam.authentication.application.port.out.PasswordCredentialRepository;
import com.opensocket.aievent.core.iam.authentication.domain.CredentialSubjectType;
import com.opensocket.aievent.core.iam.authentication.domain.RootBootstrapState;
import com.opensocket.aievent.core.iam.authentication.event.AuthenticationSecurityEvent;
import com.opensocket.aievent.core.iam.identity.application.command.CreateRootIdentityCommand;
import com.opensocket.aievent.core.iam.identity.application.port.in.IdentityCommandPort;
import com.opensocket.aievent.core.iam.identity.application.port.in.IdentityQueryPort;
import com.opensocket.aievent.core.iam.persistence.dao.IamApiRuntimeDao;
import com.opensocket.aievent.core.iam.runtime.config.IamRuntimeProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Creates the singleton Platform Root exactly once from an installation Secret.
 * Once Root exists, configured installer password material is deliberately ignored.
 */
public final class RootInstallationBootstrapper {
    private final IamRuntimeProperties properties;
    private final IdentityQueryPort identityQueries;
    private final IdentityCommandPort identityCommands;
    private final PasswordAuthenticationCommandPort passwords;
    private final RootBootstrapStateRepository bootstrapStates;
    private final PasswordCredentialRepository credentials;
    private final RootAuthenticationCommandPort rootAuthentication;
    private final AuthenticationEventPublisher events;
    private final IamApiRuntimeDao dao;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public RootInstallationBootstrapper(
            IamRuntimeProperties properties,
            IdentityQueryPort identityQueries,
            IdentityCommandPort identityCommands,
            PasswordAuthenticationCommandPort passwords,
            RootBootstrapStateRepository bootstrapStates,
            PasswordCredentialRepository credentials,
            RootAuthenticationCommandPort rootAuthentication,
            AuthenticationEventPublisher events,
            IamApiRuntimeDao dao,
            TransactionTemplate transactions,
            Clock clock) {
        this.properties = properties;
        this.identityQueries = identityQueries;
        this.identityCommands = identityCommands;
        this.passwords = passwords;
        this.bootstrapStates = bootstrapStates;
        this.credentials = credentials;
        this.rootAuthentication = rootAuthentication;
        this.events = events;
        this.dao = dao;
        this.transactions = transactions;
        this.clock = clock;
    }

    public void installIfRequired() {
        if (!properties.isRootInstallationEnabled()) return;
        if (identityQueries.findRootIdentity().isPresent()) {
            requireExistingRootCredential();
            recordSecretIgnoredWhenConfigured();
            return;
        }

        char[] initialPassword = RootInstallationSecretResolver.resolve(properties);
        try {
            if (initialPassword.length == 0) {
                if (properties.isRootInstallationFailClosed()) {
                    throw new IllegalStateException(
                            "ROOT_INSTALLATION_SECRET_REQUIRED: configure AEG_IAM_ROOT_INITIAL_PASSWORD or AEG_IAM_ROOT_INITIAL_PASSWORD_FILE");
                }
                return;
            }
            if (initialPassword.length < 14) {
                throw new IllegalStateException(
                        "ROOT_INSTALLATION_SECRET_POLICY_VIOLATION: initial Root password must contain at least 14 characters");
            }
            String correlationId = "root-installation-" + UUID.randomUUID();
            Instant at = clock.instant();
            transactions.executeWithoutResult(status -> install(initialPassword, correlationId, at));
        } finally {
            Arrays.fill(initialPassword, '\0');
        }
    }

    private void install(char[] initialPassword, String correlationId, Instant at) {
        dao.acquireRootInstallationLock();
        if (identityQueries.findRootIdentity().isPresent()) {
            requireExistingRootCredential();
            recordSecretIgnoredInsideTransaction(at);
            return;
        }
        identityCommands.createRootIdentity(new CreateRootIdentityCommand(
                "root-installer", correlationId, UUID.randomUUID().toString()));
        var credential = passwords.setPassword(new SetPasswordCommand(
                CredentialSubjectType.INSTANCE_ROOT,
                "root",
                "root",
                "",
                initialPassword,
                true,
                "root-installer",
                correlationId,
                at,
                0));

        RootBootstrapState currentState = bootstrapStates.find();
        RootBootstrapState state = currentState.passwordConfigured()
                ? currentState
                : rootAuthentication.recordStep(new RootBootstrapStepCommand(
                        RootBootstrapStepCommand.Step.PASSWORD_CONFIGURED,
                        "root-installer",
                        correlationId,
                        at,
                        currentState.version()));

        Map<String, Object> row = eventRow(
                "ROOT_INSTALLATION_CREATED",
                credential.version(),
                correlationId,
                at,
                "{\"mustChange\":true}");
        row.put("actorId", "root-installer");
        if (dao.updateRootInstallationIdentity(row) != 1) {
            throw new IllegalStateException("ROOT_INSTALLATION_IDENTITY_UPDATE_FAILED");
        }
        if (dao.updateRootInstallationState(row) != 1) {
            throw new IllegalStateException("ROOT_INSTALLATION_STATE_UPDATE_FAILED");
        }
        if (dao.insertRootInstallationEvent(row) != 1) {
            throw new IllegalStateException("ROOT_INSTALLATION_EVIDENCE_FAILED");
        }
        events.publish(new AuthenticationSecurityEvent(
                UUID.randomUUID().toString(),
                "ROOT_INSTALLATION_CREATED",
                CredentialSubjectType.INSTANCE_ROOT.name(),
                "root",
                "",
                "root-installer",
                correlationId,
                "INSTALLATION_SECRET_CONSUMED",
                Map.of(
                        "credentialVersion", Long.toString(credential.version()),
                        "bootstrapStateVersion", Long.toString(state.version())),
                at));
    }

    private void requireExistingRootCredential() {
        if (credentials.find(CredentialSubjectType.INSTANCE_ROOT, "root").isEmpty()) {
            throw new IllegalStateException(
                    "ROOT_INSTALLATION_INCOMPLETE: Root exists without a password credential; use the controlled recovery procedure");
        }
    }

    private void recordSecretIgnoredWhenConfigured() {
        if (!properties.hasConfiguredRootInstallationSecret()) return;
        Instant at = clock.instant();
        transactions.executeWithoutResult(status -> recordSecretIgnoredInsideTransaction(at));
    }

    private void recordSecretIgnoredInsideTransaction(Instant at) {
        Map<String, Object> row = eventRow(
                "ROOT_INSTALLATION_SECRET_IGNORED",
                0,
                "root-installation-restart",
                at,
                "{\"reason\":\"ROOT_ALREADY_EXISTS\"}");
        dao.insertRootInstallationSecretIgnored(row);
    }

    private static Map<String, Object> eventRow(
            String eventType,
            long credentialVersion,
            String correlationId,
            Instant occurredAt,
            String detailsJson) {
        Map<String, Object> row = new HashMap<>();
        row.put("eventId", UUID.randomUUID().toString());
        row.put("eventType", eventType);
        row.put("credentialVersion", credentialVersion);
        row.put("correlationId", correlationId);
        row.put("detailsJson", detailsJson);
        row.put("occurredAt", occurredAt);
        return row;
    }
}
