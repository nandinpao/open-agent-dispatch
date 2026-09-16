package com.opensocket.aievent.core.a2a.compatibility;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import com.opensocket.aievent.core.a2a.*;
import com.opensocket.aievent.core.a2a.application.port.in.A2ACompatibilityFacade;

/** Sole historical mutation bridge. New callers must use canonical A2A inbound ports directly. */
@Deprecated(forRemoval=true)
@Component
@ConditionalOnProperty(prefix="a2a.compatibility",name="legacy-entrypoints-enabled",havingValue="true",matchIfMissing=false)
public final class LegacyA2AMutationFacade {
    private final A2ACompatibilityFacade canonical;
    public LegacyA2AMutationFacade(A2ACompatibilityFacade canonical){this.canonical=canonical;}
    public A2ARequest request(A2ARequestCommand command){return canonical.request(command);}
    public A2ARequest approve(String tenant,String request,String actorType,String actor,String key){return canonical.approve(tenant,request,actorType,actor,key);}
    public A2ARequest reject(String tenant,String request,String code,String reason,String actorType,String actor,String key){return canonical.reject(tenant,request,code,reason,actorType,actor,key);}
    public A2ARequest cancel(String tenant,String request,String actorType,String actor,String reason,String key){return canonical.cancel(tenant,request,actorType,actor,reason,key);}
    public A2AResult acceptResult(A2AResultSubmission submission){return canonical.acceptResult(submission);}
}
