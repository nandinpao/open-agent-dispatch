package com.opensocket.aievent.core.capability;

import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** PC-S5 time-based revocation sweep for facts whose expiry does not emit a database UPDATE event. */
@Component
public class A2AAuthorityRevocationSweep {
    private final JdbcTemplate tenants; private final NamedParameterJdbcTemplate jdbc; private final A2ATrustAssurancePolicyService assurance; private final TransactionTemplate transactions;
    public A2AAuthorityRevocationSweep(JdbcTemplate tenants,NamedParameterJdbcTemplate jdbc,A2ATrustAssurancePolicyService assurance,PlatformTransactionManager transactionManager){this.tenants=tenants;this.jdbc=jdbc;this.assurance=assurance;this.transactions=new TransactionTemplate(transactionManager);}

    @Scheduled(fixedDelayString="${opendispatch.a2a-authority-revocation-sweep-ms:30000}", scheduler="a2aRemoteOperationalScheduler")
    public void run(){for(String tenant:tenants.queryForList("select tenant_id from tenants where status='ACTIVE' order by tenant_id",String.class))sweepTenant(tenant);}

    public void sweepTenant(String tenant){transactions.executeWithoutResult(status -> sweepTenantInTransaction(tenant));}

    private void sweepTenantInTransaction(String tenant){bind(tenant);Set<String> interfaces=new LinkedHashSet<>(jdbc.queryForList("""
      select distinct interface_id from a2a_peer_provider_links where tenant_id=:tenant and status='ACTIVE'
      union
      select distinct interface_id from a2a_remote_read_executions where tenant_id=:tenant and status not in ('SUCCEEDED','FAILED','DEAD_LETTER')
      """,new MapSqlParameterSource("tenant",tenant),String.class));
      for(String interfaceId:interfaces){
        String peer=jdbc.queryForObject("select peer_id from a2a_peer_interfaces where tenant_id=:tenant and interface_id=:interface",params(tenant,interfaceId),String.class);
        Boolean contract=jdbc.queryForObject("select a2a_interface_current_contract_eligible(:tenant,:interface)",params(tenant,interfaceId),Boolean.class);
        boolean trust=true;try{assurance.requireReadAllowed(tenant,peer,interfaceId);}catch(IllegalArgumentException ex){trust=false;}
        if(!Boolean.TRUE.equals(contract)||!trust)jdbc.queryForObject("select pc_s5_revoke_a2a_authority(:tenant,:interface,:reason)",params(tenant,interfaceId).addValue("reason",!trust?"PC_S5_TRUST_ASSURANCE_EXPIRED_OR_REVOKED":"PC_S5_INTERFACE_AUTHORITY_EXPIRED_OR_REVOKED"),Object.class);
      }
    }
    private void bind(String tenant){jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_tenant_id', ?, true)",String.class,tenant);jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_actor_id', ?, true)",String.class,"a2a-authority-revocation-sweep");}
    private static MapSqlParameterSource params(String t,String i){return new MapSqlParameterSource("tenant",t).addValue("interface",i);}
}
