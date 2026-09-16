package com.opensocket.aievent.core.a2a.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.opensocket.aievent.core.a2a.*;
import com.opensocket.aievent.core.a2a.application.port.in.A2ACutoverUseCase;

@RestController @RequestMapping("/api/a2a-cutover")
public class A2ACutoverController {
    private final A2ACutoverUseCase cutover;private final A2AApiRequestContextAccessor context;
    public A2ACutoverController(A2ACutoverUseCase c,A2AApiRequestContextAccessor x){cutover=c;context=x;}
    @GetMapping public A2ACutoverSnapshot current(@RequestParam(defaultValue="100")int evidenceLimit){return execute(()->cutover.current("INSTANCE",evidenceLimit));}
    @PostMapping("/advance") public A2ACutoverState advance(@RequestHeader("If-Match")long expectedVersion,@RequestBody Advance body){if(body==null||body.targetStage()==null)throw bad("targetStage is required.");return execute(()->cutover.advance(new A2ACutoverTransitionCommand("INSTANCE",expectedVersion,body.targetStage(),body.shadowMismatchCount(),body.issueTrackingEnabled(),body.migrationEvidenceReference(),body.runtimeGateRunId(),body.releaseEvidenceReference(),body.rollbackDeadline(),actor(),body.reason())));}
    private String actor(){A2AApiRequestContext c=context.current();return c==null||c.actorId()==null||c.actorId().isBlank()?"unknown-operator":c.actorId().trim();}
    private ResponseStatusException bad(String m){return new ResponseStatusException(HttpStatus.BAD_REQUEST,m);}
    private <T>T execute(Op<T> op){try{return op.run();}catch(IllegalArgumentException ex){throw bad(ex.getMessage());}catch(IllegalStateException ex){throw new ResponseStatusException(HttpStatus.CONFLICT,ex.getMessage(),ex);}}
    @FunctionalInterface interface Op<T>{T run();}
    public record Advance(A2ACutoverStage targetStage,long shadowMismatchCount,boolean issueTrackingEnabled,String migrationEvidenceReference,String runtimeGateRunId,String releaseEvidenceReference,java.time.OffsetDateTime rollbackDeadline,String reason){}
}
