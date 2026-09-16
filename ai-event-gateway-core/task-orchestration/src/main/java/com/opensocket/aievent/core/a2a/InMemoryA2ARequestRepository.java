package com.opensocket.aievent.core.a2a;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository @Profile("!prod")
@ConditionalOnProperty(prefix="task",name="store",havingValue="MEMORY")
public class InMemoryA2ARequestRepository implements A2ARequestRepository {
    private final ConcurrentHashMap<String,A2ARequest> values=new ConcurrentHashMap<>();
    public synchronized A2ARequest save(A2ARequest r){Optional<A2ARequest> replay=findByIdempotencyKey(r.getTenantId(),r.getIdempotencyKey());if(replay.isPresent()&&!replay.get().getRequestId().equals(r.getRequestId()))return replay.get();values.put(key(r.getTenantId(),r.getRequestId()),r);return r;}
    public synchronized A2ARequest saveExpectedVersion(A2ARequest request,long expectedVersion){A2ARequest current=values.get(key(request.getTenantId(),request.getRequestId()));if(current==null||current.getVersion()!=expectedVersion)throw new IllegalStateException("RESOURCE_VERSION_CONFLICT");values.put(key(request.getTenantId(),request.getRequestId()),request);return request;}
    public Optional<A2ARequest> findById(String t,String id){return Optional.ofNullable(values.get(key(t,id)));}
    public Optional<A2ARequest> findByIdempotencyKey(String t,String k){if(k==null)return Optional.empty();return values.values().stream().filter(v->t.equals(v.getTenantId())&&k.equals(v.getIdempotencyKey())).findFirst();}
    public Optional<A2ARequest> findByChildTask(String t,String id){if(id==null)return Optional.empty();return values.values().stream().filter(v->t.equals(v.getTenantId())&&id.equals(v.getChildTaskId())).findFirst();}
    public List<A2ARequest> findByRootTask(String t,String id,int n){return values.values().stream().filter(v->t.equals(v.getTenantId())&&id.equals(v.getRootTaskId())).sorted(Comparator.comparing(A2ARequest::getCreatedAt)).limit(cap(n)).toList();}
    public List<A2ARequest> findBySourceTask(String t,String id,int n){return values.values().stream().filter(v->t.equals(v.getTenantId())&&id.equals(v.getSourceTaskId())).sorted(Comparator.comparing(A2ARequest::getCreatedAt).reversed()).limit(cap(n)).toList();}
    public List<A2ARequest> search(String t,String status,String text,int n){String q=text==null?"":text.trim().toLowerCase();return values.values().stream().filter(v->t.equals(v.getTenantId())).filter(v->status==null||status.isBlank()||v.getRequestStatus().name().equals(status)).filter(v->q.isBlank()||contains(v.getRequestId(),q)||contains(v.getRootTaskId(),q)||contains(v.getSourceTaskId(),q)||contains(v.getChildTaskId(),q)||contains(v.getTargetDomainId(),q)||contains(v.getRequestedTaskType(),q)).sorted(Comparator.comparing(A2ARequest::getUpdatedAt,Comparator.nullsLast(Comparator.naturalOrder())).reversed()).limit(cap(n)).toList();}
    private boolean contains(String value,String q){return value!=null&&value.toLowerCase().contains(q);}
    public String mode(){return "MEMORY";} private String key(String t,String id){return t+":"+id;} private int cap(int n){return Math.max(1,Math.min(n,1000));}
}
