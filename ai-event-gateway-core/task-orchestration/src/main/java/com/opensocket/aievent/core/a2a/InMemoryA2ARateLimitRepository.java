package com.opensocket.aievent.core.a2a;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository @Profile("!prod")
@ConditionalOnProperty(prefix="task",name="store",havingValue="MEMORY")
public class InMemoryA2ARateLimitRepository implements A2ARateLimitRepository {
    private final ConcurrentHashMap<String,AtomicInteger> windows=new ConcurrentHashMap<>();
    public boolean tryAcquire(String t,String p,int limit,OffsetDateTime now){String key=t+":"+p+":"+now.truncatedTo(ChronoUnit.MINUTES);return windows.computeIfAbsent(key,k->new AtomicInteger()).incrementAndGet()<=Math.max(1,limit);}
    public String mode(){return "MEMORY";}
}
