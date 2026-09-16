package com.opensocket.aievent.core.action.executor.issue.scoped;

import com.opensocket.aievent.core.issuetracking.change.ExternalChangeProviderGateway;
import com.opensocket.aievent.core.issuetracking.change.ProviderCommentSyncCommand;
import com.opensocket.aievent.core.issuetracking.change.ProviderRelationSyncCommand;
import com.opensocket.aievent.core.issuetracking.change.ProviderSyncResult;
import com.opensocket.aievent.core.issuetracking.relay.RelayProviderCommand;
import com.opensocket.aievent.core.issuetracking.relay.RelayProviderGateway;
import com.opensocket.aievent.core.issuetracking.relay.RelayProviderResult;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Scoped Phase 3H relay execution.
 *
 * <p>Every relay edge executes independently through the Phase 3G
 * provider-neutral comment/relation operations.</p>
 */
@Component
@Primary
public class ScopedRelayProviderGateway implements RelayProviderGateway {

    private final ExternalChangeProviderGateway delegate;

    public ScopedRelayProviderGateway(ExternalChangeProviderGateway delegate) {
        this.delegate = delegate;
    }

    @Override
    public RelayProviderResult execute(RelayProviderCommand command) {
        return switch (command.edgeType()) {
            case SOURCE_BACKLINK -> comment(
                    command,
                    command.sourceExternalIssueId(),
                    "source-backlink");
            case TARGET_BACKLINK -> comment(
                    command,
                    targetIssueOrSource(command),
                    "target-backlink");
            case PROVIDER_NATIVE_RELATION -> relation(command);
            case COMPENSATION_COMMENT -> comment(
                    command,
                    command.sourceExternalIssueId(),
                    "compensation");
            case COMPENSATION_TRANSITION -> RelayProviderResult.failure(
                    false,
                    "CONTROLLED_DECISION_REQUIRED",
                    "External transition compensation requires a controlled Provider Action Candidate; "
                            + "Phase 3H does not close external issues directly.");
        };
    }

    private RelayProviderResult comment(
            RelayProviderCommand command,
            String issueId,
            String purpose) {
        ProviderCommentSyncCommand providerCommand = new ProviderCommentSyncCommand(
                command.tenantId(),
                command.connectionId(),
                command.projectMappingId(),
                command.externalProjectId(),
                issueId,
                command.edgeId(),
                buildCommentBody(command, purpose),
                command.sourceMarker(),
                command.idempotencyKey() + ":" + purpose,
                command.correlationId());

        return convert(delegate.appendComment(providerCommand));
    }

    private RelayProviderResult relation(RelayProviderCommand command) {
        if (command.targetExternalIssueId().isBlank()) {
            return RelayProviderResult.failure(
                    false,
                    "PROVIDER_NATIVE_RELATION_TARGET_MISSING",
                    "Provider-native relation requires a target external issue.");
        }

        ProviderRelationSyncCommand providerCommand = new ProviderRelationSyncCommand(
                command.tenantId(),
                command.connectionId(),
                command.projectMappingId(),
                command.externalProjectId(),
                command.sourceExternalIssueId(),
                command.targetExternalIssueId(),
                command.relationType(),
                command.sourceMarker(),
                command.idempotencyKey() + ":native",
                command.correlationId());

        return convert(delegate.createRelation(providerCommand));
    }

    private String targetIssueOrSource(RelayProviderCommand command) {
        return command.targetExternalIssueId().isBlank()
                ? command.sourceExternalIssueId()
                : command.targetExternalIssueId();
    }

    private String buildCommentBody(RelayProviderCommand command, String purpose) {
        String message = command.message();
        String backlinkUrl = command.backlinkUrl();

        if (!message.isBlank() && !backlinkUrl.isBlank()) {
            return message + "\n" + backlinkUrl;
        }
        if (!message.isBlank()) {
            return message;
        }
        if (!backlinkUrl.isBlank()) {
            return backlinkUrl;
        }
        return "OpenDispatch relay " + purpose + " for topology " + command.topologyId();
    }

    private RelayProviderResult convert(ProviderSyncResult result) {
        return result.success()
                ? RelayProviderResult.success(result.providerObjectId())
                : RelayProviderResult.failure(
                        result.retryable(),
                        result.reasonCode(),
                        result.safeMessage());
    }

    @Override
    public String mode() {
        return "SCOPED_PROVIDER_NEUTRAL_RELAY";
    }
}
