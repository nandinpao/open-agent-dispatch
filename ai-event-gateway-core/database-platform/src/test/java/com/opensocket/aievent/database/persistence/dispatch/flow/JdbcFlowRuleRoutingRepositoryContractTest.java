package com.opensocket.aievent.database.persistence.dispatch.flow;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;

class JdbcFlowRuleRoutingRepositoryContractTest {

    @Test
    void canonicalCandidateSqlUsesOnlyPersistedFlowRulesAndNeverSourceDefaultPseudoRules() throws Exception {
        String sql = privateString("CANDIDATE_RULE_SQL");

        assertTrue(sql.contains("from dispatch_policies p"));
        assertTrue(sql.contains("flow_required_capabilities"));
        assertTrue(sql.contains("p.service_code"));
        assertTrue(sql.contains("coalesce(p.priority,100)"));
        assertTrue(sql.contains("false as source_default_pool"));
        assertFalse(sql.toLowerCase().contains("source_default as"));
        assertFalse(sql.toLowerCase().contains("limit 1"));
        assertFalse(sql.contains("updated_at desc"));
    }

    @Test
    void runtimeLookupScopesTenantCaseInsensitivelyWithoutUsingRequestedSkillAsSelector() throws Exception {
        String sql = privateString("CANDIDATE_RULE_SQL");

        assertTrue(sql.contains("where upper(f.tenant_id) in (:tenantIds)"));
        assertTrue(sql.contains("where upper(p.tenant_id) in (:tenantIds)"));
        assertFalse(sql.contains(":requestedSkill"));
        assertFalse(sql.contains("requested_skill ="));
    }

    @Test
    void c7DraftSimulationRelaxesOnlyFlowLifecycleAndKeepsRuleLifecycleAuthoritative() throws Exception {
        String sql = privateString("CANDIDATE_RULE_SQL");

        assertTrue(sql.contains(":allowDraftFlow=true"));
        assertTrue(sql.contains("(:hasFlowId=false or f.flow_id=:flowId)"));
        assertTrue(sql.contains("upper(coalesce(p.status,'DRAFT')) in ('ACTIVE','ENABLED')"));
    }

    @Test
    void runtimeRuleSelectionResolvesRuleIssuePolicyOverrideThenFlowDefault() throws Exception {
        String sql = privateString("CANDIDATE_RULE_SQL");

        assertTrue(sql.contains("p.issue_sync_policy"));
        assertTrue(sql.contains("f.issue_sync_policy"));
        String sourceFlowsCte = sql.substring(sql.indexOf("with source_flows as ("), sql.indexOf("\n      select p.tenant_id"));
        assertTrue(sourceFlowsCte.contains("f.issue_sync_policy"),
                "source_flows CTE must project dispatch_flows.issue_sync_policy for the outer Flow alias");
        assertTrue(sql.contains("upper(nullif(p.issue_sync_policy,'')) as rule_issue_sync_policy"));
        assertTrue(sql.contains("upper(nullif(f.issue_sync_policy,'')) as flow_issue_sync_policy"));
        assertTrue(sql.contains("then 'RULE_OVERRIDE'"));
        assertTrue(sql.contains("then 'FLOW_DEFAULT'"));
        assertTrue(sql.contains("else 'SYSTEM_FALLBACK'"));
        assertTrue(sql.contains("as issue_sync_policy_source"));
        assertTrue(sql.contains("coalesce(nullif(p.issue_sync_policy,''),nullif(f.issue_sync_policy,''),'OPTIONAL')"));
    }

    private String privateString(String fieldName) throws Exception {
        Field sqlField = JdbcFlowRuleRoutingRepository.class.getDeclaredField(fieldName);
        sqlField.setAccessible(true);
        return (String) sqlField.get(null);
    }
}
