package com.opensocket.aievent.core.resourceaccess.persistence;
import com.opensocket.aievent.core.resourceaccess.contract.*;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;
import java.sql.Timestamp; import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.transaction.annotation.Transactional;
@DatabaseRepositoryAdapter
@ConditionalOnProperty(prefix="resource-access",name={"enabled","integration-enabled"},havingValue="true")
public class JdbcExternalWriteAuthorizationAuditAdapter implements ExternalWriteAuthorizationAuditPort {
 private final JdbcTemplate jdbc; public JdbcExternalWriteAuthorizationAuditAdapter(JdbcTemplate jdbc){this.jdbc=Objects.requireNonNull(jdbc,"jdbc");}
 @Override @Transactional public void append(ExternalWriteAuthorizationCommand c,ExternalWriteAuthorizationContext x){
  String mode=x.auditEvidence().getOrDefault("decisionMode","SHADOW"),effect=x.auditEvidence().getOrDefault("decisionEffect","ERROR");
  String policy=x.auditEvidence().getOrDefault("policyVersion","0:0"),epoch=x.auditEvidence().getOrDefault("securityEpoch","0");
  String[] pv=policy.replace("PolicyVersion[","").replace("]","").split("[:;,]"); long catalog=number(pv,0),revision=number(pv,1);
  jdbc.update("""
   insert into resource_external_write_authorization_audits(tenant_id,audit_id,authorization_decision_id,human_principal_id,resource_type,resource_id,permission_code,purpose,decision_effect,decision_mode,policy_catalog_version,policy_revision,security_epoch_fingerprint,executable,correlation_id,idempotency_key,created_at)
   values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) on conflict(tenant_id,idempotency_key) do nothing
   """,x.resourceRef().tenantId(),UUID.randomUUID().toString(),x.authorizationDecisionId(),x.humanPrincipal().principalId(),x.resourceRef().resourceType().name(),x.resourceRef().resourceId(),x.action().permissionCode(),x.purpose(),effect,mode,catalog,revision,epoch,x.executable(),x.correlationId(),c.idempotencyKey(),Timestamp.from(x.authorizedAt()));
 }
 private static long number(String[] p,int i){if(i>=p.length)return 0;String s=p[i].replaceAll("[^0-9]","");return s.isBlank()?0:Long.parseLong(s);}
}
