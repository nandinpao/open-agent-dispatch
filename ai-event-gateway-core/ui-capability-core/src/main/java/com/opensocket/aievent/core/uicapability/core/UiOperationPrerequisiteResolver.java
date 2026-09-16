package com.opensocket.aievent.core.uicapability.core;

import com.opensocket.aievent.core.iam.security.contract.AuthenticationContext;

/** Additional operation requirements are projections only; mutation endpoints must enforce them again. */
public interface UiOperationPrerequisiteResolver {
    UiOperationPrerequisites resolve(ResolvedUiAction action, AuthenticationContext authentication);
}
