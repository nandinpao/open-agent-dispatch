package com.opensocket.aievent.core.iam.organization.application.port.in;

import com.opensocket.aievent.core.iam.organization.application.command.AddTenantMembershipCommand;
import com.opensocket.aievent.core.iam.organization.application.command.ChangeTenantMembershipStatusCommand;
import com.opensocket.aievent.core.iam.organization.application.command.ChangeTenantStatusCommand;
import com.opensocket.aievent.core.iam.organization.application.command.CreateTenantCommand;
import com.opensocket.aievent.core.iam.organization.application.command.UpdateTenantMembershipCommand;
import com.opensocket.aievent.core.iam.organization.domain.Tenant;
import com.opensocket.aievent.core.iam.organization.domain.TenantMembership;

public interface TenantCommandPort {
    Tenant createTenant(CreateTenantCommand command);
    Tenant changeTenantStatus(ChangeTenantStatusCommand command);
    TenantMembership addTenantMembership(AddTenantMembershipCommand command);
    TenantMembership updateTenantMembership(UpdateTenantMembershipCommand command);
    TenantMembership changeTenantMembershipStatus(ChangeTenantMembershipStatusCommand command);
}
