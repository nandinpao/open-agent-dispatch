package com.opensocket.aievent.database.persistence.dispatch.flow;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;

class JdbcFlowRuleRoutingRepositoryContractTest {

    @Test
    void sourceDefaultAndNoneRulesDoNotRequireCapabilityRows() throws Exception {
        String sql = privateString("SQL");

        assertTrue(sql.contains("capability_requirement_mode in ('SOURCE_DEFAULT', 'NONE')"));
    }

    @Test
    void runtimeLookupComparesTenantCaseInsensitivelyWithoutHyphenToUnderscoreNormalization() throws Exception {
        String sql = privateString("SQL");
        String diagnosticsSql = privateString("NO_MATCH_DIAGNOSTIC_SQL");

        assertTrue(sql.contains("where upper(p.tenant_id) in (:tenantIds)"));
        assertTrue(diagnosticsSql.contains("where upper(p.tenant_id) in (:tenantIds)"));
        assertFalse(sql.contains("where p.tenant_id in (:tenantIds)"));
        assertFalse(diagnosticsSql.contains("where p.tenant_id in (:tenantIds)"));
    }

    private String privateString(String fieldName) throws Exception {
        Field sqlField = JdbcFlowRuleRoutingRepository.class.getDeclaredField(fieldName);
        sqlField.setAccessible(true);
        return (String) sqlField.get(null);
    }
}
