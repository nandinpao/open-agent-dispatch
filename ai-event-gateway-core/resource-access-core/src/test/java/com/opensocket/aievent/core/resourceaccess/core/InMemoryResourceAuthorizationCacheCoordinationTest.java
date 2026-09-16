package com.opensocket.aievent.core.resourceaccess.core;

import static org.junit.jupiter.api.Assertions.*;
import com.opensocket.aievent.core.resourceaccess.contract.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class InMemoryResourceAuthorizationCacheCoordinationTest {
    private static final Instant NOW=Instant.parse("2026-07-29T00:00:00Z");

    @Test void sameKeyConcurrentMissesExecuteOneLoader()throws Exception{
        InMemoryResourceAuthorizationCache cache=new InMemoryResourceAuthorizationCache(1000,16,Duration.ofSeconds(5));
        ResourceAuthorizationCacheKey key=key("TASK-1",SecurityEpoch.ZERO);
        AtomicInteger loads=new AtomicInteger();
        ExecutorService pool=Executors.newFixedThreadPool(32);
        CountDownLatch start=new CountDownLatch(1);
        List<Future<AuthorizationDecision>> futures=new ArrayList<>();
        for(int i=0;i<256;i++)futures.add(pool.submit(()->{start.await();return cache.coordinate(key,()->{
            loads.incrementAndGet();try{Thread.sleep(30);}catch(InterruptedException e){Thread.currentThread().interrupt();}
            AuthorizationDecision decision=decision(key);cache.put(key,decision,NOW.plusSeconds(30));return decision;});}));
        start.countDown();for(Future<AuthorizationDecision> future:futures)assertEquals(DecisionEffect.ALLOW,future.get(10,TimeUnit.SECONDS).effect());pool.shutdownNow();
        assertEquals(1,loads.get());assertTrue(cache.metrics().followerWaits()>=200);assertEquals(1,cache.metrics().coordinatedLoads());
    }

    @Test void epochChangeCannotReusePrewarmedAllow(){
        InMemoryResourceAuthorizationCache cache=new InMemoryResourceAuthorizationCache(100);
        ResourceAuthorizationCacheKey oldKey=key("TASK-1",SecurityEpoch.ZERO);cache.prewarm(List.of(new ResourceAuthorizationCacheEntry(oldKey,decision(oldKey),NOW.plusSeconds(30))));
        ResourceAuthorizationCacheKey newKey=key("TASK-1",new SecurityEpoch(0,1,0,0,0,0));
        assertTrue(cache.get(newKey,NOW).isEmpty());assertEquals(1,cache.metrics().prewarmedEntries());
    }

    private static ResourceAuthorizationCacheKey key(String id,SecurityEpoch epoch){return new ResourceAuthorizationCacheKey("T1","USER","U1","task.read",ResourceType.TASK,id,VisibilityLevel.SUMMARY,PolicyVersion.ZERO,epoch);}
    private static AuthorizationDecision decision(ResourceAuthorizationCacheKey key){return new AuthorizationDecision(UUID.randomUUID().toString(),DecisionEffect.ALLOW,AuthorizationDecisionMode.FORMAL,key.permissionCode(),new ResourceRef(key.tenantId(),key.resourceType(),key.resourceId()),VisibilityLevel.SUMMARY,Set.of(),Set.of(),Set.of(),Set.of(),Set.of(),Set.of("TENANT"),key.policyVersion(),key.securityEpoch(),"descriptor-hash","",List.of(),NOW,false,true,Duration.ofSeconds(30));}
}
