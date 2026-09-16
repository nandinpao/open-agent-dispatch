package com.opensocket.aievent.core.iam.runtime.idempotency;

import com.opensocket.aievent.core.iam.api.idempotency.IamIdempotencyPort;
import com.opensocket.aievent.core.iam.persistence.dao.IamApiRuntimeDao;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantContextHolder;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantExecutionContext;
import java.time.Clock;import java.time.Instant;import java.util.HashMap;import java.util.Map;import java.util.function.Supplier;
import org.springframework.transaction.support.TransactionTemplate;

/** Same-transaction Reserve -> Domain Command -> Persist Response implementation. */
public final class TransactionalIamIdempotencyAdapter implements IamIdempotencyPort {
 private final IamApiRuntimeDao dao;private final TransactionTemplate transactions;private final Clock clock;
 public TransactionalIamIdempotencyAdapter(IamApiRuntimeDao dao,TransactionTemplate transactions,Clock clock){this.dao=dao;this.transactions=transactions;this.clock=clock;}
 @Override public Execution executeAtomically(String scope,String actor,String operation,String key,String requestHash,Instant expiresAt,Supplier<StoredResponse> command){
   boolean instance=scope==null||scope.isBlank()||"INSTANCE".equalsIgnoreCase(scope);
   Supplier<Execution> work=()->transactions.execute(status->execute(instance,scope,actor,operation,key,requestHash,expiresAt,command));
   return instance?work.get():IamTenantContextHolder.withContext(new IamTenantExecutionContext(scope,actor),work);
 }
 private Execution execute(boolean instance,String scope,String actor,String operation,String key,String hash,Instant expires,Supplier<StoredResponse> command){
   Instant now=clock.instant();Map<String,Object> row=new HashMap<>();row.put("scopeId",instance?"INSTANCE":scope);if(!instance)row.put("tenantId",scope);row.put("actorId",actor);row.put("operation",operation);row.put("key",key);row.put("requestHash",hash);row.put("createdAt",now);row.put("expiresAt",expires);
   if(instance)dao.insertInstanceIdempotency(row);else dao.insertTenantIdempotency(row);
   Map<String,Object> stored=instance?dao.lockInstanceIdempotency("INSTANCE",actor,operation,key):dao.lockTenantIdempotency(scope,actor,operation,key);
   if(stored==null)throw new IllegalStateException("IAM_IDEMPOTENCY_RESERVATION_MISSING");
   if(!hash.equals(string(stored,"requestHash")))return new Execution(State.CONFLICT,null);
   if("COMPLETED".equals(string(stored,"status")))return new Execution(State.REPLAY,response(stored));
   StoredResponse result=command.get();if(result.responseBody().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>1048576)throw new IllegalStateException("IAM_IDEMPOTENCY_RESPONSE_TOO_LARGE");
   row.put("httpStatus",result.httpStatus());row.put("contentType",result.contentType());row.put("responseBody",result.responseBody());row.put("completedAt",clock.instant());
   int updated=instance?dao.completeInstanceIdempotency(row):dao.completeTenantIdempotency(row);if(updated!=1)throw new IllegalStateException("IAM_IDEMPOTENCY_COMPLETION_FAILED");
   return new Execution(State.COMPLETED,result);
 }
 private StoredResponse response(Map<String,Object> r){return new StoredResponse(number(r,"httpStatus"),string(r,"contentType"),string(r,"responseBody"));}
 private static String string(Map<String,Object>r,String k){Object v=r.get(k);return v==null?"":String.valueOf(v);}private static int number(Map<String,Object>r,String k){Object v=r.get(k);return v instanceof Number n?n.intValue():Integer.parseInt(String.valueOf(v));}
}
