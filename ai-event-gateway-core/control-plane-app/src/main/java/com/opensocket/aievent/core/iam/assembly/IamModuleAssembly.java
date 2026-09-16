package com.opensocket.aievent.core.iam.assembly;

import java.util.List;

import com.opensocket.aievent.core.iam.api.IamApiModule;
import com.opensocket.aievent.core.iam.authentication.AuthenticationModule;
import com.opensocket.aievent.core.iam.identity.IdentityCoreModule;
import com.opensocket.aievent.core.iam.organization.OrganizationAccessModule;
import com.opensocket.aievent.core.iam.persistence.IamPersistenceModule;
import com.opensocket.aievent.core.iam.rbac.RbacModule;
import com.opensocket.aievent.core.iam.security.contract.AuthenticationContext;
import com.opensocket.aievent.core.iam.token.AccessTokenModule;

/** Compile-time composition manifest. Runtime beans are introduced in later Phase 1 stages. */
public final class IamModuleAssembly {
    private IamModuleAssembly() {
    }

    public static List<Class<?>> moduleMarkers() {
        return List.of(
                AuthenticationContext.class,
                IdentityCoreModule.class,
                OrganizationAccessModule.class,
                RbacModule.class,
                AuthenticationModule.class,
                AccessTokenModule.class,
                IamPersistenceModule.class,
                IamApiModule.class);
    }
}
