/**
 * Framework-neutral P4RA / RS Resource Access contracts.
 *
 * <p>RS0 freezes one authorization vocabulary across IAM/RBAC and business-resource access. This
 * package must not depend on Spring, MyBatis or business entities, and domain modules must project
 * authoritative ownership/scope evidence into these contracts rather than create parallel ACLs.</p>
 */
package com.opensocket.aievent.core.resourceaccess.contract;
