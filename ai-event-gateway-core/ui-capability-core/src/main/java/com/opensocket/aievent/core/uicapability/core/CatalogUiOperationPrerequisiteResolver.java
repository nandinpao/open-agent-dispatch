package com.opensocket.aievent.core.uicapability.core;

import com.opensocket.aievent.core.iam.security.contract.AuthenticationAssurance;
import com.opensocket.aievent.core.iam.security.contract.AuthenticationContext;
import java.util.Objects;

/**
 * Conservative baseline: a configured step-up profile requires MFA/SYSTEM assurance and a configured
 * approval profile remains pending until a future domain-specific approval-state adapter proves satisfaction.
 */
public final class CatalogUiOperationPrerequisiteResolver implements UiOperationPrerequisiteResolver {
    @Override public UiOperationPrerequisites resolve(ResolvedUiAction action, AuthenticationContext authentication) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(authentication, "authentication");
        boolean stepUpConfigured = !action.definition().stepUpProfile().isBlank();
        AuthenticationAssurance.Level level = authentication.assurance().level();
        boolean assuranceSatisfied = level == AuthenticationAssurance.Level.MFA
                || level == AuthenticationAssurance.Level.SYSTEM;
        boolean stepUpRequired = stepUpConfigured && !assuranceSatisfied;
        boolean approvalRequired = !stepUpRequired && !action.definition().approvalProfile().isBlank();
        return new UiOperationPrerequisites(stepUpRequired, approvalRequired);
    }
}
