package com.opensocket.aievent.core.governance;
import java.util.*; import java.util.concurrent.ConcurrentHashMap; import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; import org.springframework.context.annotation.Profile; import org.springframework.stereotype.Repository;
@Repository @Profile("!prod") @ConditionalOnProperty(prefix="core.audit-evidence",name="store",havingValue="MEMORY",matchIfMissing=true)
public class InMemoryAuditEvidenceRepository implements AuditEvidenceRepository {
 private final Map<String,AuthorizationDecision> decisions=new ConcurrentHashMap<>(); private final Map<String,AuditEvidence> evidence=new ConcurrentHashMap<>(); private final Map<String,ApiMutationReceipt> receipts=new ConcurrentHashMap<>();
 public AuthorizationDecision saveDecision(AuthorizationDecision v){decisions.putIfAbsent(k(v.tenantId(),v.decisionId()),v);return decisions.get(k(v.tenantId(),v.decisionId()));}
 public Optional<AuthorizationDecision> findDecision(String t,String id){return Optional.ofNullable(decisions.get(k(t,id)));}
 public AuditEvidence saveEvidence(AuditEvidence v){evidence.putIfAbsent(k(v.tenantId(),v.evidenceId()),v);return evidence.get(k(v.tenantId(),v.evidenceId()));}
 public List<AuditEvidence> listEvidence(String t,String type,String id,int limit){return evidence.values().stream().filter(v->t.equals(v.tenantId())).filter(v->type==null||type.equals(v.aggregateType())).filter(v->id==null||id.equals(v.aggregateId())).sorted(Comparator.comparing(AuditEvidence::occurredAt).reversed()).limit(Math.max(1,Math.min(limit,1000))).toList();}
 public synchronized ApiMutationReceipt saveReceipt(ApiMutationReceipt v){String natural=natural(v.tenantId(),v.requestMethod(),v.requestPath(),v.idempotencyKey());for(var e:receipts.values())if(natural(e).equals(natural)){if(!Objects.equals(e.requestHash(),v.requestHash()))throw new IllegalStateException("API_IDEMPOTENCY_CONFLICT");if(terminal(e.status()))return e;}receipts.put(k(v.tenantId(),v.receiptId()),v);return v;}
 public Optional<ApiMutationReceipt> findReceiptByIdempotency(String t,String m,String p,String i){String n=natural(t,m,p,i);return receipts.values().stream().filter(v->n.equals(natural(v))).findFirst();}
 public Optional<ApiMutationReceipt> findReceipt(String t,String id){return Optional.ofNullable(receipts.get(k(t,id)));}
 public String mode(){return "MEMORY";} private String k(String a,String b){return a+"::"+b;} private String natural(ApiMutationReceipt v){return natural(v.tenantId(),v.requestMethod(),v.requestPath(),v.idempotencyKey());} private String natural(String t,String m,String p,String i){return t+"::"+m+"::"+p+"::"+i;} private boolean terminal(String s){return Set.of("COMPLETED","FAILED","CONFLICT","REPLAYED").contains(s);}
}
