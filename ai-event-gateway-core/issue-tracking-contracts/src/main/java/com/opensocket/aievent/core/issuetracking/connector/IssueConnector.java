package com.opensocket.aievent.core.issuetracking.connector;

/**
 * Thin external Issue execution contract.
 *
 * <p>This contract translates controlled OpenDispatch Issue operations into provider calls.
 * It is deliberately not an authorization API: provider permissions remain authoritative in
 * the configured external Issue system.</p>
 */
public interface IssueConnector {
    String provider();

    IssueConnectorResult read(IssueReadCommand command);

    IssueConnectorResult create(IssueCreateCommand command);

    IssueConnectorResult comment(IssueCommentCommand command);

    IssueConnectorResult update(IssueUpdateCommand command);
}
