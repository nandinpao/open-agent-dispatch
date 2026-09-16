package com.opensocket.aievent.core.integration.identity;
import java.time.OffsetDateTime; import java.util.*; import org.springframework.transaction.annotation.Transactional;
/** Operator-side provisioning for opaque Webhook endpoints. The API accepts only a SHA-256 token digest, never the raw endpoint token. */
public class IntegrationWebhookEndpointService {
 private final IntegrationWebhookEndpointRepository endpoints; private final IntegrationIdentityRepository identities;
 public IntegrationWebhookEndpointService(IntegrationWebhookEndpointRepository endpoints,IntegrationIdentityRepository identities){this.endpoints=endpoints;this.identities=identities;}
 public IntegrationWebhookEndpoint endpoint(String tenant,String id){return endpoints.find(req(tenant,"tenantId"),req(id,"endpointId")).orElseThrow(()->new IllegalArgumentException("Integration Webhook Endpoint not found in Tenant: "+id));}
 public List<IntegrationWebhookEndpoint> endpoints(String tenant,String connectionId,int limit){return endpoints.list(req(tenant,"tenantId"),connectionId,Math.max(1,Math.min(limit,1000)));}
 @Transactional public IntegrationWebhookEndpoint save(String tenant,String id,IntegrationWebhookEndpoint body,Long expected){
  tenant=req(tenant,"tenantId");id=req(id,"endpointId");String tokenHash=req(body.endpointTokenHash(),"endpointTokenHash").toLowerCase(Locale.ROOT);
  if(!tokenHash.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("endpointTokenHash must be a lowercase SHA-256 hexadecimal digest.");
  var connection=identities.findConnection(tenant,req(body.connectionId(),"connectionId")).orElseThrow(()->new IllegalArgumentException("Integration Connection not found in Tenant: "+body.connectionId()));
  var principal=identities.findPrincipal(tenant,req(body.principalId(),"principalId")).orElseThrow(()->new IllegalArgumentException("Integration Principal not found in Tenant: "+body.principalId()));
  if(!principal.connectionId().equals(connection.connectionId()))throw new IllegalArgumentException("Webhook Principal must belong to the Endpoint Connection.");
  var status=body.status()==null?IntegrationWebhookEndpointStatus.DRAFT:body.status();
  if(status==IntegrationWebhookEndpointStatus.ACTIVE){
   if(!connection.enabled()||connection.status()!=IntegrationConnectionStatus.ACTIVE)throw new IllegalStateException("WEBHOOK_CONNECTION_NOT_ACTIVE");
   if(principal.status()!=IntegrationPrincipalStatus.ACTIVE)throw new IllegalStateException("WEBHOOK_PRINCIPAL_NOT_ACTIVE");
   var scope=identities.findPrincipalScope(tenant,principal.principalId()).orElseThrow(()->new IllegalStateException("WEBHOOK_PRINCIPAL_SCOPE_REQUIRED"));
   if(!scope.productionAllowed()||!scope.allowsOperation(IntegrationOperation.WEBHOOK))throw new IllegalStateException("WEBHOOK_PRINCIPAL_SCOPE_INVALID");
   boolean credential=identities.listCredentials(tenant,principal.principalId(),100).stream().anyMatch(this::usableCredential);
   if(!credential)throw new IllegalStateException("WEBHOOK_ACTIVE_OR_GRACE_CREDENTIAL_REQUIRED");
  }
  var old=endpoints.find(tenant,id);long oldVersion=old.map(IntegrationWebhookEndpoint::version).orElse(0L);
  old.ifPresent(existing->{if(!existing.endpointTokenHash().equals(tokenHash)||!existing.connectionId().equals(connection.connectionId())||!existing.principalId().equals(principal.principalId())||existing.providerType()!=connection.providerType())throw new IllegalStateException("WEBHOOK_ENDPOINT_IDENTITY_IMMUTABLE");});
  if(expected!=null&&expected.longValue()!=oldVersion)throw new IllegalStateException("WEBHOOK_ENDPOINT_VERSION_CONFLICT");
  var now=OffsetDateTime.now();
  return endpoints.save(new IntegrationWebhookEndpoint(tenant,id,tokenHash,connection.connectionId(),principal.principalId(),connection.providerType(),status,
   "HMAC_SHA256_V1",bound(body.maxBodyBytes(),1024,10485760,1048576),bound(body.rateLimitPerMinute(),1,100000,120),bound(body.replayWindowSeconds(),30,3600,300),oldVersion+1,old.map(IntegrationWebhookEndpoint::createdAt).orElse(now),now));
 }
 private boolean usableCredential(IntegrationCredentialMetadata c){var now=OffsetDateTime.now();return (c.status()==IntegrationCredentialStatus.ACTIVE||c.status()==IntegrationCredentialStatus.GRACE_PERIOD)&&(c.validFrom()==null||!c.validFrom().isAfter(now))&&(c.expiresAt()==null||c.expiresAt().isAfter(now));}
 private int bound(int v,int min,int max,int d){return v<=0?d:Math.max(min,Math.min(v,max));} private long bound(long v,long min,long max,long d){return v<=0?d:Math.max(min,Math.min(v,max));} private String req(String v,String n){if(v==null||v.isBlank())throw new IllegalArgumentException(n+" is required.");return v.trim();}
}
