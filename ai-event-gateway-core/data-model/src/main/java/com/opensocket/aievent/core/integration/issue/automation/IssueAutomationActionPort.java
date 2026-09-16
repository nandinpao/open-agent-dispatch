package com.opensocket.aievent.core.integration.issue.automation;

/**
 * Application port used by Task orchestration to request a canonical Issue
 * AdapterAction without depending on the adapter-action Maven module.
 */
public interface IssueAutomationActionPort {
    IssueAutomationActionResult request(IssueAutomationActionCommand command);
}
