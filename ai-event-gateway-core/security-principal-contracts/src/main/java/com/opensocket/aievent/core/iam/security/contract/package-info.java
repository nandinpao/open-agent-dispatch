/**
 * Stable, framework-free security contracts shared by IAM modules and the composition root.
 * Domain entities, Spring Security types, persistence records and OAuth SDK types are prohibited.
 *
 * <p>Human authentication continues to use {@link com.opensocket.aievent.core.iam.security.contract.AuthenticationContext}.
 * Machine callers use {@link com.opensocket.aievent.core.iam.security.contract.MachineAuthenticationContext};
 * credential bounds are upper limits and never replace RBAC/resource authorization decisions.</p>
 */
package com.opensocket.aievent.core.iam.security.contract;
