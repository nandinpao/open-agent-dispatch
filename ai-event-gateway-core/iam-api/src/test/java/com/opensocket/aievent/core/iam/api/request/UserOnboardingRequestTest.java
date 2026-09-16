package com.opensocket.aievent.core.iam.api.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.opensocket.aievent.core.iam.identity.domain.UserCreationMode;
import com.opensocket.aievent.core.iam.organization.domain.MembershipStatus;
import com.opensocket.aievent.core.iam.organization.domain.TenantMembershipSource;
import java.util.List;
import org.junit.jupiter.api.Test;

class UserOnboardingRequestTest {

    @Test
    void invitationOnboardingRequiresInvitationMembershipContract() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> request(
                        UserCreationMode.INVITATION,
                        MembershipStatus.ACTIVE,
                        TenantMembershipSource.INVITATION,
                        false));

        assertEquals(
                "Invitation onboarding requires INVITED membership, INVITATION source and defaultTenant=false",
                error.getMessage());
    }

    @Test
    void invitationOnboardingCannotCreateDefaultTenantBeforeAcceptance() {
        assertThrows(
                IllegalArgumentException.class,
                () -> request(
                        UserCreationMode.INVITATION,
                        MembershipStatus.INVITED,
                        TenantMembershipSource.INVITATION,
                        true));
    }

    @Test
    void adminCreatedIdentityCannotCreateInvitationMembership() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> request(
                        UserCreationMode.ADMIN_CREATED,
                        MembershipStatus.INVITED,
                        TenantMembershipSource.INVITATION,
                        false));

        assertEquals(
                "Only invitation onboarding may create an INVITED Tenant Membership",
                error.getMessage());
    }

    @Test
    void nullAssignmentListsAreNormalizedToImmutableEmptyLists() {
        UserOnboardingRequest request = new UserOnboardingRequest(
                "user-1",
                "user.one",
                "user.one@example.com",
                "User One",
                UserCreationMode.ADMIN_CREATED,
                "LOCAL",
                "MANUAL",
                null,
                "membership-1",
                MembershipStatus.ACTIVE,
                "E001",
                null,
                true,
                TenantMembershipSource.ADMIN_CREATED,
                null,
                null,
                null,
                true,
                "Create employee account");

        assertEquals(List.of(), request.departments());
        assertEquals(List.of(), request.groups());
        assertEquals(List.of(), request.roles());
        assertThrows(UnsupportedOperationException.class, () -> request.roles().add(null));
    }


    @Test
    void absentUserIdIsAcceptedForServerGeneration() {
        UserOnboardingRequest request = new UserOnboardingRequest(
                null,
                "generated.user",
                "generated.user@example.com",
                "Generated User",
                UserCreationMode.ADMIN_CREATED,
                "LOCAL",
                "MANUAL",
                null,
                "membership-generated",
                MembershipStatus.ACTIVE,
                null,
                null,
                false,
                TenantMembershipSource.ADMIN_CREATED,
                List.of(),
                List.of(),
                List.of(),
                true,
                "Create generated identity");

        assertEquals(null, request.userId());
        assertEquals("SERVER_GENERATED_USER", request.authorizationTarget());
    }

    @Test
    void validInvitationContractIsAccepted() {
        UserOnboardingRequest request = request(
                UserCreationMode.INVITATION,
                MembershipStatus.INVITED,
                TenantMembershipSource.INVITATION,
                false);

        assertEquals(UserCreationMode.INVITATION, request.creationMode());
        assertEquals(MembershipStatus.INVITED, request.membershipStatus());
    }

    @Test
    void emailDeliveryRequiresEmailAddress() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new UserOnboardingRequest(
                        null, "no.email", null, "No Email", UserCreationMode.ADMIN_CREATED,
                        "LOCAL", "EMAIL", null, "membership-email", MembershipStatus.ACTIVE, null, null, false,
                        TenantMembershipSource.ADMIN_CREATED, List.of(), List.of(), List.of(), true, "Create account"));
        assertEquals("IDENTITY_ACTIVATION_EMAIL_REQUIRED", error.getMessage());
    }

    @Test
    void manualDeliveryDoesNotRequireEmailAndLocalIsCanonicalAuthenticationMethod() {
        UserOnboardingRequest request = new UserOnboardingRequest(
                null, "manual.user", null, "Manual User", UserCreationMode.ADMIN_CREATED,
                "local", "manual", null, "membership-manual", MembershipStatus.ACTIVE, null, null, false,
                TenantMembershipSource.ADMIN_CREATED, List.of(), List.of(), List.of(), true, "Create account");
        assertEquals("LOCAL", request.authenticationMethod());
        assertEquals("MANUAL", request.activationDeliveryMethod());
    }

    @Test
    void temporaryPasswordIsAllowedOnlyForAdminCreatedAccounts() {
        UserOnboardingRequest request = new UserOnboardingRequest(
                null, "temporary.user", "temporary.user@example.com", "Temporary User",
                UserCreationMode.ADMIN_CREATED, "LOCAL", "MANUAL", "Temporary-Only-2026!",
                "membership-temporary", MembershipStatus.ACTIVE, null, null, false,
                TenantMembershipSource.ADMIN_CREATED, List.of(), List.of(), List.of(), true, "Create test account");
        assertEquals("Temporary-Only-2026!", request.initialPassword());
        assertEquals(true, ((java.util.Map<?, ?>) request.idempotencyMaterial()).get("initialPasswordConfigured"));
        assertEquals(false, ((java.util.Map<?, ?>) request.idempotencyMaterial()).containsValue("Temporary-Only-2026!"));
    }


    @Test
    void administratorTemporaryPasswordDoesNotRequireSetupDeliveryEmail() {
        UserOnboardingRequest request = new UserOnboardingRequest(
                null, "local.test", null, "Local Test", UserCreationMode.ADMIN_CREATED,
                "LOCAL", "EMAIL", "Temporary-Only-2026!", "membership-local", MembershipStatus.ACTIVE, null, null, false,
                TenantMembershipSource.ADMIN_CREATED, List.of(), List.of(), List.of(), true, "Create local test account");
        assertEquals("EMAIL", request.activationDeliveryMethod());
        assertEquals(false, request.toString().contains("Temporary-Only-2026!"));
    }

    @Test
    void invitationCannotCarryAdministratorTemporaryPassword() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                new UserOnboardingRequest(
                        null, "invited.user", "invited.user@example.com", "Invited User",
                        UserCreationMode.INVITATION, "LOCAL", "EMAIL", "Temporary-Only-2026!",
                        "membership-invite", MembershipStatus.INVITED, null, null, false,
                        TenantMembershipSource.INVITATION, List.of(), List.of(), List.of(), false, "Invite user"));
        assertEquals("IDENTITY_INITIAL_PASSWORD_ADMIN_CREATED_ONLY", error.getMessage());
    }

    private static UserOnboardingRequest request(
            UserCreationMode creationMode,
            MembershipStatus membershipStatus,
            TenantMembershipSource membershipSource,
            boolean defaultTenant) {
        return new UserOnboardingRequest(
                "user-1",
                "user.one",
                "user.one@example.com",
                "User One",
                creationMode,
                "LOCAL",
                "MANUAL",
                null,
                "membership-1",
                membershipStatus,
                "E001",
                null,
                defaultTenant,
                membershipSource,
                List.of(),
                List.of(),
                List.of(),
                creationMode == UserCreationMode.ADMIN_CREATED && membershipStatus == MembershipStatus.ACTIVE,
                "Create employee account");
    }
}
