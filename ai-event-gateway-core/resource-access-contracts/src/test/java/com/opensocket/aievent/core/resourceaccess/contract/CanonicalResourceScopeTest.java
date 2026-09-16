package com.opensocket.aievent.core.resourceaccess.contract;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class CanonicalResourceScopeTest {
    @Test
    void frozenVocabularyContainsExactlySixCanonicalScopes() {
        assertEquals(
                Set.of("INSTANCE", "TENANT", "DEPARTMENT", "DEPARTMENT_SUBTREE", "GROUP", "RESOURCE"),
                Stream.of(CanonicalResourceScope.values()).map(Enum::name).collect(Collectors.toSet()));
    }

    @Test
    void v19RoleBindingCeilingRemainsOrganizationScopedAndResourceIsExplicitException() {
        assertTrue(CanonicalResourceScope.INSTANCE.roleBindingEligible());
        assertTrue(CanonicalResourceScope.TENANT.roleBindingEligible());
        assertTrue(CanonicalResourceScope.DEPARTMENT.roleBindingEligible());
        assertTrue(CanonicalResourceScope.DEPARTMENT_SUBTREE.roleBindingEligible());
        assertTrue(CanonicalResourceScope.GROUP.roleBindingEligible());
        assertFalse(CanonicalResourceScope.RESOURCE.roleBindingEligible());
        assertTrue(CanonicalResourceScope.RESOURCE.resourceAccessEligible());
    }

    @Test
    void contextualAndRelationshipScopesCannotMasqueradeAsCanonicalOrganizationAuthority() {
        assertEquals(ScopeSemanticClass.CONTEXTUAL_CONSTRAINT, ScopeSemantics.classify(ScopeType.ASSIGNED_TO_ME));
        assertEquals(ScopeSemanticClass.CONTEXTUAL_CONSTRAINT, ScopeSemantics.classify(ScopeType.CREATED_BY_ME));
        assertEquals(ScopeSemanticClass.DOMAIN_RELATIONSHIP, ScopeSemantics.classify(ScopeType.TASK_CHAIN));
        assertEquals(ScopeSemanticClass.RESOURCE_EXCEPTION, ScopeSemantics.classify(ScopeType.RESOURCE));
        assertFalse(CanonicalResourceScope.fromResourceAccessScope(ScopeType.ASSIGNED_TO_ME).isPresent());
    }
}
